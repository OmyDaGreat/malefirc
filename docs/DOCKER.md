# Docker Setup for Malefirc

This directory contains Docker configuration to run the complete Malefirc IRC system using Docker Compose.

## What's Included

The Docker setup includes:
- **PostgreSQL Database** - For user authentication and data persistence
- **IRC Server** - Running on port 6667
- **Web Client** - Kobweb-based web interface on port 8080

## Quick Start

### Prerequisites

- Docker Engine (20.10+)
- Docker Compose (2.0+)

### Starting the System

```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f

# View logs for specific service
docker-compose logs -f irc-server
```

The services will be available at:
- IRC Server: `localhost:6667` (connect with any IRC client)
- Web Client: http://localhost:8080
- PostgreSQL: `localhost:5432`

### Stopping the System

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (deletes database data)
docker-compose down -v
```

## Service Details

### PostgreSQL Database

- **Image**: postgres:15-alpine
- **Port**: 5432
- **Database**: malefirc
- **User**: malefirc
- **Password**: malefirc
- **Volume**: postgres_data (persistent storage)

### IRC Server

- **Port**: 6667
- **Built from**: Dockerfile.server
- **Dependencies**: PostgreSQL (waits for health check)
- Connects to PostgreSQL automatically

### Web Client

- **Port**: 8080 (HTTP)
- **Built from**: Dockerfile.client
- Served via Nginx
- Kobweb-based web interface

## Connecting to the IRC Server

### Using Standard IRC Clients

```bash
# Using irssi
irssi
/connect localhost 6667
/nick YourNick
/join #general

# Using WeeChat
weechat
/server add malefirc localhost/6667
/connect malefirc
/join #general

# Using HexChat
Add server: localhost/6667
```

### Using the Web Client

Simply navigate to http://localhost:8080 in your browser.

## Configuration

### Environment Variables

You can customize the database configuration by editing `docker-compose.yml`:

```yaml
environment:
  POSTGRES_DB: malefirc
  POSTGRES_USER: malefirc
  POSTGRES_PASSWORD: malefirc  # Change this in production!
```

### Ports

To change exposed ports, modify the `ports` section in `docker-compose.yml`:

```yaml
ports:
  - "6667:6667"  # IRC Server (host:container)
  - "8080:80"    # Web Client (host:container)
  - "5432:5432"  # PostgreSQL (host:container)
```

## Development

### Rebuilding After Code Changes

```bash
# Rebuild specific service
docker-compose build irc-server

# Rebuild and restart
docker-compose up -d --build irc-server

# Rebuild everything
docker-compose build --no-cache
docker-compose up -d
```

### Viewing Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f irc-server
docker-compose logs -f web-client
docker-compose logs -f postgres

# Last 100 lines
docker-compose logs --tail=100 irc-server
```

### Accessing Containers

```bash
# IRC Server
docker exec -it malefirc-server sh

# PostgreSQL
docker exec -it malefirc-postgres psql -U malefirc -d malefirc

# Web Client
docker exec -it malefirc-client sh
```

## Troubleshooting

### Services Won't Start

```bash
# Check service status
docker-compose ps

# View detailed logs
docker-compose logs

# Restart specific service
docker-compose restart irc-server
```

### Database Connection Issues

```bash
# Check PostgreSQL health
docker-compose exec postgres pg_isready -U malefirc

# Check database logs
docker-compose logs postgres
```

### Port Conflicts

If ports are already in use, modify `docker-compose.yml`:

```yaml
ports:
  - "16667:6667"  # Use port 16667 instead
  - "18080:80"    # Use port 18080 instead
```

### Cleaning Up

```bash
# Stop and remove containers
docker-compose down

# Remove volumes (WARNING: deletes data)
docker-compose down -v

# Remove images
docker-compose down --rmi all

# Complete cleanup
docker-compose down -v --rmi all --remove-orphans
```

## Production Considerations

For production deployment:

1. **Change default passwords** in `docker-compose.yml`
2. **Use Docker secrets** for sensitive data
3. **Enable SSL/TLS** for IRC server
4. **Use HTTPS** for web client (add SSL to nginx config)
5. **Set up proper backups** for postgres_data volume
6. **Configure resource limits**:

```yaml
services:
  irc-server:
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
```

7. **Use environment-specific config files**:

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Network Architecture

All services communicate through the `malefirc-network` bridge network:

```
┌─────────────┐     ┌─────────────┐
│ Web Client  │────▶│ IRC Server  │
│   :8080     │     │   :6667     │
└─────────────┘     └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  PostgreSQL │
                    │   :5432     │
                    └─────────────┘
```

## Backup and Restore

### Backup Database

```bash
# Create backup
docker-compose exec postgres pg_dump -U malefirc malefirc > backup.sql

# Or using docker volume
docker run --rm -v malefirc_postgres_data:/data -v $(pwd):/backup alpine tar czf /backup/postgres-backup.tar.gz /data
```

### Restore Database

```bash
# Restore from SQL
docker-compose exec -T postgres psql -U malefirc malefirc < backup.sql

# Or restore volume
docker run --rm -v malefirc_postgres_data:/data -v $(pwd):/backup alpine tar xzf /backup/postgres-backup.tar.gz -C /
```

## License

MIT License - See LICENSE file for details
