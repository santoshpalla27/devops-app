from kafka import KafkaConsumer
import json
import logging
from typing import List, Dict
from datetime import datetime
import threading

logger = logging.getLogger(__name__)


class KafkaObserver:
    """Observes control plane events via Kafka."""
    
    def __init__(self, bootstrap_servers: str, topic: str):
        self.bootstrap_servers = bootstrap_servers
        self.topic = topic
        self.events = []
        self.observing = False
        self.consumer = None
        self.thread = None
        
    def start_observing(self):
        """Start consuming Kafka events."""
        self.observing = True
        self.consumer = KafkaConsumer(
            self.topic,
            bootstrap_servers=self.bootstrap_servers,
            value_deserializer=lambda m: json.loads(m.decode('utf-8')),
            consumer_timeout_ms=1000
        )
        
        # Run in separate thread
        self.thread = threading.Thread(target=self._consume_events)
        self.thread.start()
    
    def stop_observing(self):
        """Stop consuming events."""
        self.observing = False
        if self.thread:
            self.thread.join(timeout=5)
        if self.consumer:
            self.consumer.close()
    
    def _consume_events(self):
        """Consume events from Kafka."""
        while self.observing:
            try:
                for message in self.consumer:
                    event = message.value
                    event['_captured_at'] = datetime.now().isoformat()
                    self.events.append(event)
                    logger.debug(f"Captured event: {event.get('eventType')}")
            except Exception as e:
                logger.error(f"Error consuming Kafka events: {e}")
    
    def get_events_by_type(self, event_type: str) -> List[Dict]:
        """Get all events of a specific type."""
        return [e for e in self.events if e.get('eventType') == event_type]
    
    def count_events(self, event_type: str) -> int:
        """Count events of a specific type."""
        return len(self.get_events_by_type(event_type))
    
    def get_all_events(self) -> List[Dict]:
        """Get all captured events."""
        return self.events
