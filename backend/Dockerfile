FROM python:3.12-slim

WORKDIR /app

RUN pip install --no-cache-dir fastapi==0.115.6 uvicorn[standard]==0.34.0 sqlalchemy==2.0.36 psycopg[binary]==3.2.3 alembic==1.14.0 python-jose[cryptography]==3.3.0 passlib[bcrypt]==1.7.4 python-binance==1.0.19 httpx==0.28.1

COPY app ./app
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
