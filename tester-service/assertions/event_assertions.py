from typing import List, Dict
import logging

logger = logging.getLogger(__name__)


class EventAssertion:
    """Assertions for Kafka events."""
    
    def assert_event_count(
        self,
        events: List[Dict],
        event_type: str,
        expected_count: int
    ) -> tuple[bool, str]:
        """Verify exact count of events."""
        actual_count = sum(1 for e in events if e.get('eventType') == event_type)
        
        if actual_count == expected_count:
            return True, f"✅ Event '{event_type}' count: {expected_count}"
        
        return False, f"❌ Event '{event_type}' expected {expected_count}, got {actual_count}"
    
    def assert_event_exists(
        self,
        events: List[Dict],
        event_type: str
    ) -> tuple[bool, str]:
        """Verify an event was emitted."""
        exists = any(e.get('eventType') == event_type for e in events)
        
        if exists:
            return True, f"✅ Event '{event_type}' was emitted"
        
        return False, f"❌ Event '{event_type}' was not emitted"
    
    def assert_event_order(
        self,
        events: List[Dict],
        event_types: List[str]
    ) -> tuple[bool, str]:
        """Verify events occurred in expected order."""
        actual_order = [e.get('eventType') for e in events if e.get('eventType') in event_types]
        
        if actual_order == event_types:
            return True, f"✅ Events in correct order: {event_types}"
        
        return False, f"❌ Expected order {event_types}, got {actual_order}"
    
    def assert_no_duplicate_events(
        self,
        events: List[Dict],
        event_type: str
    ) -> tuple[bool, str]:
        """Verify no duplicate events (based on eventId)."""
        event_ids = [
            e.get('eventId')
            for e in events
            if e.get('event Type') == event_type and 'eventId' in e
        ]
        
        if len(event_ids) == len(set(event_ids)):
            return True, f"✅ No duplicate '{event_type}' events"
        
        return False, f"❌ Found duplicate '{event_type}' events"
