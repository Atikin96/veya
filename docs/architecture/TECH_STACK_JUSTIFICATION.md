# VEYA — Technology Stack Justification

## 1. Overview

VEYA is a web application for personal nutrition and physical activity planning. It helps users manage meals, monitor nutritional values, organize workouts, and adapt their daily plans when unexpected changes occur.

The technology stack was selected to support a modular architecture, maintainability, reliable data management, and future development of the Dynamic Plan Recovery feature.

## 2. Frontend

### React

React was selected for building the user interface because it supports reusable components and a modular application structure. This makes it easier to maintain separate areas of VEYA, such as authentication, meal planning, workout management, and the dashboard.

### TypeScript

TypeScript adds static typing to JavaScript. It helps detect potential errors during development, improves code readability, and makes communication between frontend components and API services more predictable.

### Vite

Vite provides a fast development environment and an optimized production build process. It supports the React and TypeScript setup and simplifies local frontend development.

### React Router

React Router is used to organize navigation between application pages without requiring full page reloads. It supports a clear structure for the main sections of VEYA.

### Axios

Axios is used for HTTP communication with the backend REST API. It provides a centralized way to configure requests, handle responses, and manage API errors.

### CSS

CSS is used to implement the visual design and responsive layouts of the application. It allows the interface to adapt to different screen sizes.

## 3. Backend

### Java 21

Java 21 was selected as the backend programming language because it provides a mature ecosystem, strong typing, and long-term maintainability. It is suitable for implementing business logic and REST API services.

### Spring Boot

Spring Boot simplifies backend development by providing dependency injection, application configuration, and REST API support. Its structure supports separation of controllers, services, and data-access components.

The backend is organized as a modular application, allowing functionality such as authentication, meal planning, workout management, and plan recovery to be developed separately.

## 4. Database

### PostgreSQL

PostgreSQL was selected as the relational database management system because VEYA works with structured and interconnected data, including users, meals, nutritional information, workout plans, activity records, and recovery proposals.

It supports relational constraints and transactions, helping maintain data consistency.

### Flyway

Flyway is used for database schema versioning and migration management. It allows database changes to be tracked and applied in a controlled and repeatable way.

## 5. API Architecture

### REST API and JSON

The frontend and backend communicate through a REST API using JSON. This approach provides a clear separation between the user interface and backend business logic.

OpenAPI 3.1 is used to describe the API contract in a machine-readable YAML format. It documents operations, parameters, request and response schemas, and expected status codes.

## 6. Testing Tools

### Vitest and React Testing Library

Vitest is used for frontend automated tests. React Testing Library helps verify component behaviour from the user's perspective.

### Playwright

Playwright supports end-to-end browser testing. It can be used to verify important user flows across the application.

### Maven

Maven manages backend dependencies and the build lifecycle. It also provides a standard way to execute backend tests.

## 7. Development and Deployment Support

### Docker Compose

Docker Compose is used to define and run supporting development services, such as the PostgreSQL database. It helps make the local development environment more consistent.

### Git and GitHub

Git is used for version control, while GitHub hosts the project repository and supports collaboration, code review, and documentation management.

## 8. Technology Selection Summary

| Area                    | Technology                    | Purpose                           |
| ----------------------- | ----------------------------- | --------------------------------- |
| Frontend                | React                         | User interface                    |
| Programming language    | TypeScript                    | Type safety                       |
| Build tool              | Vite                          | Development and production builds |
| Navigation              | React Router                  | Client-side routing               |
| HTTP client             | Axios                         | API communication                 |
| Styling                 | CSS                           | Layout and visual presentation    |
| Backend                 | Java 21                       | Backend implementation            |
| Backend framework       | Spring Boot                   | REST API and business logic       |
| Database                | PostgreSQL                    | Persistent data storage           |
| Database migrations     | Flyway                        | Schema versioning                 |
| API specification       | OpenAPI 3.1                   | API contract documentation        |
| Frontend testing        | Vitest, React Testing Library | Component and unit testing        |
| End-to-end testing      | Playwright                    | Browser-based testing             |
| Backend build           | Maven                         | Dependency and build management   |
| Development environment | Docker Compose                | Local supporting services         |
| Version control         | Git, GitHub                   | Source code management            |

## 9. Implementation Status

The technologies listed above represent the selected project stack. Their implementation status differs across project components.

The React and TypeScript frontend is under development. The backend uses Java 21 and Spring Boot, with PostgreSQL and Flyway included in the planned application architecture.

The API specification documents the intended business operations. At the current documentation stage, the health endpoints are implemented, while the business API endpoints are planned and are not yet available for live integration testing.

This distinction is maintained to ensure that the documentation accurately reflects the current project state.
