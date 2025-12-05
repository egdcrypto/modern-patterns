# Newman Integration Tests

API integration tests for the Threshold Configuration service using Newman (Postman CLI).

## Prerequisites

```bash
npm install -g newman
```

## Running Tests

### Local Development

```bash
newman run threshold-api-collection.json \
  -e local-environment.json \
  --reporters cli,json \
  --reporter-json-export results.json
```

### CI/CD Pipeline

```bash
newman run threshold-api-collection.json \
  -e ci-environment.json \
  --reporters cli,junit \
  --reporter-junit-export results.xml
```

### With HTML Report

```bash
npm install -g newman-reporter-htmlextra

newman run threshold-api-collection.json \
  -e local-environment.json \
  --reporters cli,htmlextra \
  --reporter-htmlextra-export report.html
```

## Test Scenarios

### 1. World Registration
- Register Fantasy and Horror worlds with different world types
- Validates creation responses

### 2. Region Registration
- Register regions within worlds
- Tests parent-child hierarchy setup

### 3. Location Registration
- Register locations within regions
- Completes the dimensional hierarchy

### 4. Threshold Inheritance
- Verifies Fantasy world inherits correct default thresholds
- Tests region-level overrides (Mordor EXTREME danger)
- Confirms inheritance chain: GLOBAL → WORLD_TYPE → WORLD → REGION → LOCATION

### 5. AI Confidence Validation
- Tests confidence above threshold (should pass)
- Tests confidence below threshold (should fail)
- Tests exact boundary condition

### 6. Character Limit Validation
- Tests adding characters under limit
- Tests adding characters at limit
- Tests location-specific override

### 7. Event Trigger Validation
- Tests random values below probability (should trigger)
- Tests random values above probability (should not trigger)

## Collection Structure

```
threshold-api-collection.json
├── World Registration
│   ├── Register Fantasy World
│   └── Register Horror World
├── Region Registration
│   ├── Register Shire Region
│   └── Register Mordor Region
├── Location Registration
│   ├── Register Bag End Location
│   └── Register Mount Doom Location
├── Threshold Inheritance
│   ├── Get Fantasy World Thresholds
│   ├── Set Region Override
│   └── Verify Region Override Inheritance
├── AI Confidence Validation
│   ├── Confidence Above Threshold
│   ├── Confidence Below Threshold
│   └── Confidence at Boundary
├── Character Limit Validation
│   ├── Can Add Character
│   ├── Cannot Add Character
│   ├── Set Location Override
│   └── Verify Location Override
└── Event Trigger Validation
    ├── Event Should Trigger
    └── Event Should Not Trigger
```

## Environment Variables

| Variable | Description | Local | CI |
|----------|-------------|-------|-----|
| `baseUrl` | API base URL | `http://localhost:8080` | `http://app:8080` |
