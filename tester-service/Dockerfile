FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY . .

# Make main.py executable
RUN chmod +x main.py

# Set environment variables
ENV CONTROL_PLANE_URL=http://backend:8080
ENV KAFKA_BOOTSTRAP=kafka:9092
ENV PROMETHEUS_URL=http://prometheus:9090

ENTRYPOINT ["python", "main.py"]
CMD ["ci", "--scenarios-dir", "scenarios"]
