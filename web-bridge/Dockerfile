FROM mcr.microsoft.com/playwright/python:v1.64.1-noble
RUN apt-get update && apt-get install -y --no-install-recommends xvfb x11vnc novnc websockify x11-utils fonts-liberation && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir playwright==1.64.1 fastapi==0.115.12 uvicorn==0.34.2 httpx==0.28.1
WORKDIR /app
COPY bridge.py entrypoint.sh /app/
RUN chmod +x /app/entrypoint.sh
ENTRYPOINT ["/app/entrypoint.sh"]
