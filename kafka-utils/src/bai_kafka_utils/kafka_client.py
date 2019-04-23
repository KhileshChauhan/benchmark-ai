import logging

import kafka
from typing import List, Type

from bai_kafka_utils.events import make_benchmark_event, BenchmarkPayload
from bai_kafka_utils.utils import DEFAULT_ENCODING

logger = logging.getLogger(__name__)


def create_kafka_consumer(bootstrap_servers: List[str],
                          group_id: str,
                          topic: str,
                          payload_type: Type[BenchmarkPayload]) -> kafka.KafkaConsumer:
    def json_deserializer(msg_value):
        try:

            return make_benchmark_event(payload_type).from_json(msg_value.decode(DEFAULT_ENCODING))
        except:
            logger.exception("Failed to deserialize %s", msg_value)
            return None

    return kafka.KafkaConsumer(topic, bootstrap_servers=bootstrap_servers, group_id=group_id,
                               value_deserializer=json_deserializer)


def create_kafka_producer(
        bootstrap_servers: List[str]) -> kafka.KafkaProducer:
    def json_serializer(msg_value):
        return msg_value.to_json().encode(DEFAULT_ENCODING)

    return kafka.KafkaProducer(bootstrap_servers=bootstrap_servers, value_serializer=json_serializer)
