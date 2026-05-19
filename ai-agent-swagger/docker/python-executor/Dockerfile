FROM python:3.11-slim

# Базовые системные зависимости
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Обновляем pip без лишних предупреждений
RUN pip install --upgrade pip --quiet --root-user-action=ignore --disable-pip-version-check

# HTTP / async HTTP
RUN pip install --quiet --root-user-action=ignore --disable-pip-version-check \
    requests \
    aiohttp \
    httpx \
    urllib3

# Данные и аналитика
RUN pip install --quiet --root-user-action=ignore --disable-pip-version-check \
    numpy \
    pandas \
    python-dateutil \
    pytz

# Форматы данных
RUN pip install --quiet --root-user-action=ignore --disable-pip-version-check \
    pyyaml \
    toml \
    xmltodict \
    beautifulsoup4 \
    lxml

# Утилиты
RUN pip install --quiet --root-user-action=ignore --disable-pip-version-check \
    python-dotenv \
    pydantic \
    attrs \
    click \
    tqdm \
    tabulate \
    PyJWT \
    cryptography

# Тестирование
RUN pip install --quiet --root-user-action=ignore --disable-pip-version-check \
    pytest \
    pytest-html

WORKDIR /code

