import json
import threading
import time

import pika

from app.config import get_settings
from app.agent import PaperAnalysisAgent
from app.schemas import AnalysisTaskMessage


class RabbitWorker:
    def __init__(self):
        self.settings = get_settings()
        self.agent = PaperAnalysisAgent()
        self._thread: threading.Thread | None = None
        self._running = False

    def start_background(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._running = True
        self._thread = threading.Thread(target=self.run_forever, name="rabbit-worker", daemon=True)
        self._thread.start()

    def run_forever(self) -> None:
        while self._running:
            try:
                self._consume()
            except Exception as exc:
                print(f"[rabbit-worker] reconnect after error: {exc}")
                time.sleep(5)

    def _consume(self) -> None:
        credentials = pika.PlainCredentials(self.settings.rabbitmq_user, self.settings.rabbitmq_password)
        parameters = pika.ConnectionParameters(
            host=self.settings.rabbitmq_host,
            port=self.settings.rabbitmq_port,
            credentials=credentials,
            heartbeat=60,
            blocked_connection_timeout=300,
        )
        connection = pika.BlockingConnection(parameters)
        channel = connection.channel()
        channel.queue_declare(queue=self.settings.rabbitmq_queue, durable=True)
        channel.basic_qos(prefetch_count=1)

        def callback(ch, method, properties, body):
            try:
                payload = json.loads(body.decode("utf-8"))
                message = AnalysisTaskMessage.model_validate(payload)
                self.agent.handle_task(message)
                ch.basic_ack(delivery_tag=method.delivery_tag)
            except Exception as exc:
                print(f"[rabbit-worker] task failed: {exc}")
                ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

        channel.basic_consume(queue=self.settings.rabbitmq_queue, on_message_callback=callback)
        print(f"[rabbit-worker] consuming queue: {self.settings.rabbitmq_queue}")
        channel.start_consuming()


worker = RabbitWorker()
