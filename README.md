# Scalable URL Shortener

A scalable URL shortener backend designed with real-world system design principles such as stateless services, caching, and asynchronous analytics.

## Overview
This project focuses on building a production-oriented URL shortening service while emphasizing system design concepts over UI or frontend features.

The system is designed to:
- Handle read-heavy traffic efficiently
- Remain stateless at the application layer
- Support horizontal scaling
- Capture analytics asynchronously without impacting redirect latency

## Initial Scope (v1)
- Create short URLs
- Redirect short URLs to original URLs
- Basic REST APIs (no authentication in v1)

More advanced features such as analytics aggregation, rate limiting, and multi-region scaling will be added incrementally.

## Tech Stack (tentative)
- Backend: TBD
- Cache: TBD
- Database: TBD
- Messaging: TBD

## Status
🚧 Project under active development.
