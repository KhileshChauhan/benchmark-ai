import json
import subprocess

from executor import SERVICE_NAME, __version__
from transpiler.config import TranspilerConfig
from transpiler.bai_knowledge import create_job_yaml_spec
from bai_kafka_utils.events import BenchmarkEvent, ExecutorPayload, BenchmarkJob
from bai_kafka_utils.kafka_client import create_kafka_consumer_producer
from bai_kafka_utils.kafka_service import KafkaServiceCallback, KafkaService, KafkaServiceConfig


class ExecutorEventHandler(KafkaServiceCallback):
    def __init__(self, transpiler_config):
        self.transpiler_config = transpiler_config

    def handle_event(self, event: BenchmarkEvent, kafka_service: KafkaService):
        descriptor_contents = json.loads(event.payload.toml.contents)
        fetched_data_sources = event.payload.data_sets

        yaml, job_id = create_job_yaml_spec(descriptor_contents,
                                            self.transpiler_config,
                                            fetched_data_sources)

        self._kubernetes_apply(yaml)

        job = BenchmarkJob(
            id=job_id,
            status='SUBMITTED',  # TODO: Get response from kubectl apply?
            k8s_yaml=yaml
        )

        result_payload = ExecutorPayload.from_fetcher_payload(event.payload, job)
        return BenchmarkEvent.from_event_new_payload(event, result_payload)

    def _kubernetes_apply(self, yaml):
        subprocess.run(["kubectl", "apply", "-f", yaml])

    def cleanup(self):
        pass


def create_executor(common_kafka_cfg: KafkaServiceConfig,
                    transpiler_config: TranspilerConfig) -> KafkaService:

    callbacks = [
        ExecutorEventHandler(transpiler_config)
    ]

    consumer, producer = create_kafka_consumer_producer(common_kafka_cfg, ExecutorPayload)

    return KafkaService(SERVICE_NAME,
                        __version__,
                        common_kafka_cfg.producer_topic,
                        callbacks, consumer, producer)
