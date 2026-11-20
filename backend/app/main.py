from datetime import datetime, timedelta, timezone
import os

from fastapi import Depends, FastAPI, HTTPException, Header, status
from fastapi.middleware.cors import CORSMiddleware
from jose import JWTError, jwt
from passlib.context import CryptContext
from pydantic import BaseModel
from sqlalchemy import Boolean, Column, DateTime, Float, Integer, String, Text, create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./trader.db")
engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {})
SessionLocal = sessionmaker(bind=engine)
Base = declarative_base()
passwords = CryptContext(schemes=["bcrypt"], deprecated="auto")
JWT_SECRET = os.getenv("JWT_SECRET", "development-secret-change-me")
JWT_ALGORITHM = "HS256"


class User(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True)
    email = Column(String(320), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)


class PaperPosition(Base):
    __tablename__ = "paper_positions"
    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, nullable=False, index=True)
    symbol = Column(String(24), nullable=False)
    btc_quantity = Column(Float, nullable=False, default=0.0)
    cash_usd = Column(Float, nullable=False, default=10000.0)


class Trade(Base):
    __tablename__ = "trades"
    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, nullable=False, index=True)
    symbol = Column(String(24), nullable=False)
    side = Column(String(8), nullable=False)
    quantity = Column(Float, nullable=False)
    price = Column(Float, nullable=False)
    mode = Column(String(16), nullable=False)
    created_at = Column(DateTime, nullable=False, default=lambda: datetime.now(timezone.utc))


Base.metadata.create_all(bind=engine)
app = FastAPI(title="Binance AI Trader API", version="0.2.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_credentials=False, allow_methods=["*"], allow_headers=["*"])


class RegisterRequest(BaseModel):
    email: str
    password: str


class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"


class PaperTradeRequest(BaseModel):
    symbol: str = "BTCUSDT"
    side: str
    usd_amount: float = 100.0


def db_session():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def get_user(authorization: str = Header(...), db=Depends(db_session)):
    if not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing bearer token")
    token = authorization.removeprefix("Bearer ").strip()
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        user_id = int(payload["sub"])
    except (JWTError, KeyError, ValueError, TypeError):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired token")
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Unknown user")
    return user


def create_token(user_id: int) -> str:
    payload = {"sub": str(user_id), "exp": datetime.now(timezone.utc) + timedelta(days=7)}
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)


@app.get("/health")
def health():
    return {"status": "ok", "live_trading_enabled": os.getenv("LIVE_TRADING_ENABLED", "false").lower() == "true"}


@app.post("/auth/register", response_model=AuthResponse)
def register(request: RegisterRequest, db=Depends(db_session)):
    email = request.email.strip().lower()
    if len(request.password) < 12:
        raise HTTPException(400, "Password must contain at least 12 characters")
    if db.query(User).filter(User.email == email).first():
        raise HTTPException(409, "Account already exists")
    user = User(email=email, password_hash=passwords.hash(request.password))
    db.add(user)
    db.flush()
    db.add(PaperPosition(user_id=user.id, symbol="BTCUSDT", cash_usd=10000.0, btc_quantity=0.0))
    db.commit()
    return AuthResponse(access_token=create_token(user.id))


@app.post("/auth/login", response_model=AuthResponse)
def login(request: RegisterRequest, db=Depends(db_session)):
    user = db.query(User).filter(User.email == request.email.strip().lower()).first()
    if not user or not passwords.verify(request.password, user.password_hash):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid credentials")
    return AuthResponse(access_token=create_token(user.id))


@app.get("/paper/position")
def paper_position(user: User = Depends(get_user), db=Depends(db_session)):
    position = db.query(PaperPosition).filter(PaperPosition.user_id == user.id).first()
    return {"symbol": position.symbol, "cash_usd": position.cash_usd, "btc_quantity": position.btc_quantity}


@app.post("/paper/trade")
def paper_trade(request: PaperTradeRequest, user: User = Depends(get_user), db=Depends(db_session)):
    raise HTTPException(501, "Market-price execution not configured yet")


@app.get("/trading/live/status")
def live_status():
    enabled = os.getenv("LIVE_TRADING_ENABLED", "false").lower() == "true"
    testnet = os.getenv("BINANCE_TESTNET", "true").lower() == "true"
    return {"enabled": enabled, "testnet": testnet, "withdrawals_supported": False}
