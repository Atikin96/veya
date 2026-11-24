
import { apiClient } from "./client";
import type { paths } from "../types/api/veya-api";

type RegisterRequest =
  paths["/api/v1/auth/register"]["post"]["requestBody"]["content"]["application/json"];

type RegisterResponse =
  paths["/api/v1/auth/register"]["post"]["responses"][201]["content"]["application/json"];

type LoginRequest =
  paths["/api/v1/auth/login"]["post"]["requestBody"]["content"]["application/json"];

type LoginResponse =
  paths["/api/v1/auth/login"]["post"]["responses"][200]["content"]["application/json"];

type ConsentDocumentsResponse =
  paths["/api/v1/consent-documents"]["get"]["responses"][200]["content"]["application/json"];
export const authApi = {
  getConsentDocuments: async (): Promise<ConsentDocumentsResponse> => {
    const response = await apiClient.get("/api/v1/consent-documents");
    return response.data;
  },
  register: async (data: RegisterRequest): Promise<RegisterResponse> => {
    const response = await apiClient.post<RegisterResponse>(
      "/api/v1/auth/register",
      data,
    );

    return response.data;
  },

  login: async (data: LoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post<LoginResponse>(
      "/api/v1/auth/login",
      data,
    );

    localStorage.setItem("accessToken", response.data.accessToken);

    return response.data;
  },

  logout: (): void => {
    localStorage.removeItem("accessToken");
  },
};
