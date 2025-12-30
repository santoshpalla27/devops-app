import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { SystemHealth, FailureEvent } from '../types';

const WS_URL = import.meta.env.VITE_WS_URL || '/ws';

interface UseWebSocketReturn {
    isConnected: boolean;
    health: SystemHealth | null;
    events: FailureEvent[];
    connect: () => void;
    disconnect: () => void;
    requestRefresh: () => void;
}

export function useWebSocket(): UseWebSocketReturn {
    const [isConnected, setIsConnected] = useState(false);
    const [health, setHealth] = useState<SystemHealth | null>(null);
    const [events, setEvents] = useState<FailureEvent[]>([]);
    const clientRef = useRef<Client | null>(null);

    const connect = useCallback(() => {
        if (clientRef.current?.connected) return;

        const client = new Client({
            webSocketFactory: () => new SockJS(WS_URL),
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
            debug: (str) => {
                if (import.meta.env.DEV) {
                    console.log('STOMP:', str);
                }
            },
        });

        client.onConnect = () => {
            console.log('WebSocket connected');
            setIsConnected(true);

            // Subscribe to health updates
            client.subscribe('/topic/health', (message: IMessage) => {
                try {
                    const healthData = JSON.parse(message.body) as SystemHealth;
                    setHealth(healthData);
                } catch (e) {
                    console.error('Failed to parse health message:', e);
                }
            });

            // Subscribe to failure events
            client.subscribe('/topic/events', (message: IMessage) => {
                try {
                    const event = JSON.parse(message.body) as FailureEvent;
                    setEvents((prev) => [event, ...prev].slice(0, 50)); // Keep last 50 events
                } catch (e) {
                    console.error('Failed to parse event message:', e);
                }
            });

            // Request initial health status
            client.publish({
                destination: '/app/health',
                body: '',
            });
        };

        client.onDisconnect = () => {
            console.log('WebSocket disconnected');
            setIsConnected(false);
        };

        client.onStompError = (frame) => {
            console.error('STOMP error:', frame.headers['message']);
            setIsConnected(false);
        };

        client.activate();
        clientRef.current = client;
    }, []);

    const disconnect = useCallback(() => {
        if (clientRef.current) {
            clientRef.current.deactivate();
            clientRef.current = null;
        }
        setIsConnected(false);
    }, []);

    const requestRefresh = useCallback(() => {
        if (clientRef.current?.connected) {
            clientRef.current.publish({
                destination: '/app/refresh',
                body: '',
            });
        }
    }, []);

    useEffect(() => {
        connect();
        return () => disconnect();
    }, [connect, disconnect]);

    return {
        isConnected,
        health,
        events,
        connect,
        disconnect,
        requestRefresh,
    };
}
