# VEYA Frontend — Personal Nutrition and Fitness Planner

VEYA is a web application designed to help users plan their daily nutrition and physical activity, track their progress, and adapt their plans to everyday changes.

The application focuses on making personal planning more flexible. Instead of requiring users to follow a strict daily schedule, VEYA aims to help them adjust the remaining part of their day when unexpected situations occur.

## Main Features

- **Daily Dashboard** — an overview of daily nutrition, calorie intake, macronutrients, planned meals, and activity.
- **Nutrition Tracking** — a dedicated space for managing meals and monitoring daily calorie and macronutrient intake.
- **Workout Planning** — an interface for viewing and managing physical activity.
- **History and Progress** — a space for reviewing nutrition and activity history.
- **Profile and Settings** — user profile and personal preferences.
- **Adaptive Plan Recovery** — a key feature designed to suggest adjustments to the remaining daily plan when the original plan changes.

## Adaptive Plan Recovery

One of VEYA's main concepts is Adaptive Plan Recovery.

When a user deviates from their original plan — for example, consumes more calories than expected, skips a workout, or needs to replace a meal — the application can suggest adjustments for the rest of the day.

The goal is to preserve already recorded activities and meals while helping the user create a more realistic updated plan.

Users can review proposed changes and decide whether to accept, edit, or decline them.

## Technology Stack

### Frontend

- React
- TypeScript
- Vite
- React Router
- Axios
- CSS
- Lucide React

### Testing

- Vitest
- React Testing Library
- Playwright

## Project Architecture

The frontend follows a component-based architecture and is organized into pages, reusable components, layout components, services, hooks, data, and TypeScript types.

## Project Structure

```text
frontend/
├── public/
├── src/
│   ├── assets/
│   ├── components/
│   │   ├── common/
│   │   └── layout/
│   ├── data/
│   ├── hooks/
│   ├── pages/
│   ├── services/
│   ├── styles/
│   ├── test/
│   ├── types/
│   ├── App.tsx
│   ├── App.css
│   ├── index.css
│   └── main.tsx
├── docs/
│   └── api/
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## Getting Started

### Prerequisites

- Node.js
- npm

### Installation

From the root directory of the VEYA repository:

```bash
cd frontend
npm install
```

### Development

Start the development server:

```bash
npm run dev
```

The application will be available at the local URL provided by Vite, by default `http://localhost:5173`.

### Build

```bash
npm run build
```

### Tests

```bash
npm run test:run
```

## API Documentation

The API documentation is maintained in `docs/api/`.

- **OpenAPI Specification:** `docs/api/openapi.yaml`
- **API Endpoint Catalogue:** `docs/api/API_ENDPOINTS.md`
- **Integration Tutorial:** `docs/api/INTEGRATION_TUTORIAL.md`
- **Frontend Integration Handoff:** `docs/api/FRONTEND_HANDOFF.md`
- **Postman Collection:** `docs/api/postman/VEYA_API.postman_collection.json`
- **Postman Environment:** `docs/api/postman/VEYA_Local.postman_environment.json`

The OpenAPI specification uses OpenAPI 3.1 and is the source of truth for the API contract.

To validate the API documentation, run the following command from the frontend directory:

```bash
python docs/api/tools/check_contract.py
```

**Implementation status:** The three health endpoints are implemented. The business API endpoints are documented in the contract but are still planned and are not yet available for live integration testing.

## Project Status

VEYA is currently under development as part of a university diploma project.

The frontend is being developed with React and TypeScript, with a focus on responsive design, reusable UI components, and the Adaptive Plan Recovery workflow.