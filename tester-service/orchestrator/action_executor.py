import aiohttp
import asyncio
import logging
from typing import Dict, Any

logger = logging.getLogger(__name__)


class ActionExecutor:
    """Executes test actions against the control plane."""
    
    def __init__(self, base_url: str):
        self.base_url = base_url
    
    async def execute(self, action: Dict[str, Any]) -> Dict[str, Any]:
        """Execute a single action."""
        action_type = action.get('type')
        
        if action_type == 'REST':
            return await self._execute_rest(action)
        elif action_type == 'WAIT':
            return await self._execute_wait(action)
        elif action_type == 'CHAOS':
            return await self._execute_chaos(action)
        else:
            logger.warning(f"Unknown action type: {action_type}")
            return {'success': False, 'error': f'Unknown action type: {action_type}'}
    
    async def _execute_rest(self, action: Dict) -> Dict:
        """Execute REST API call."""
        method = action.get('method', 'GET')
        endpoint = action.get('endpoint')
        payload = action.get('payload', {})
        
        url = f"{self.base_url}{endpoint}"
        
        async with aiohttp.ClientSession() as session:
            try:
                async with session.request(
                    method,
                    url,
                    json=payload if method in ['POST', 'PUT'] else None
                ) as response:
                    data = await response.json() if response.content_type == 'application/json' else {}
                    
                    logger.info(f"{method} {endpoint} -> {response.status}")
                    
                    return {
                        'success': response.status < 400,
                        'status': response.status,
                        'data': data
                    }
            except Exception as e:
                logger.error(f"REST call failed: {e}")
                return {'success': False, 'error': str(e)}
    
    async def _execute_wait(self, action: Dict) -> Dict:
        """Execute wait action."""
        duration_str = action.get('duration', '1s')
        duration_seconds = self._parse_duration(duration_str)
        
        logger.info(f"Waiting {duration_seconds}s...")
        await asyncio.sleep(duration_seconds)
        
        return {'success': True}
    
    async def _execute_chaos(self, action: Dict) -> Dict:
        """Execute chaos injection."""
        system_type = action.get('system')
        fault_type = action.get('fault')
        duration = action.get('duration', 60)
        
        payload = {
            'systemType': system_type,
            'faultType': fault_type,
            'durationSeconds': duration
        }
        
        return await self._execute_rest({
            'type': 'REST',
            'method': 'POST',
            'endpoint': '/api/chaos/inject',
            'payload': payload
        })
    
    def _parse_duration(self, duration_str: str) -> float:
        """Parse duration string like '5s', '2m' to seconds."""
        if duration_str.endswith('s'):
            return float(duration_str[:-1])
        elif duration_str.endswith('m'):
            return float(duration_str[:-1]) * 60
        elif duration_str.endswith('h'):
            return float(duration_str[:-1]) * 3600
        else:
            return float(duration_str)
