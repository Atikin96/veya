
import { apiClient } from "./client";
import type { paths } from "../types/api/veya-api";


type SavedMealsResponse =
  paths["/api/v1/saved-meals"]["get"]["responses"][200]["content"]["application/json"];

type CreateSavedMealRequest =
  paths["/api/v1/saved-meals"]["post"]["requestBody"]["content"]["application/json"];

type CreateSavedMealResponse =
  paths["/api/v1/saved-meals"]["post"]["responses"][201]["content"]["application/json"];

type LogSavedMealRequest =
  paths["/api/v1/saved-meals/{savedMealId}/entries"]["post"]["requestBody"]["content"]["application/json"];

type LogSavedMealResponse =
  paths["/api/v1/saved-meals/{savedMealId}/entries"]["post"]["responses"][201]["content"]["application/json"];


export const savedMealsApi = {

  getSavedMeals: async (): Promise<SavedMealsResponse> => {
    const response = await apiClient.get("/api/v1/saved-meals");

    return response.data;
  },

  createSavedMeal: async (
    data: CreateSavedMealRequest,
  ): Promise<CreateSavedMealResponse> => {
    const response = await apiClient.post(
      "/api/v1/saved-meals",
      data,
    );

    return response.data;
  },

  logSavedMeal: async (
    savedMealId: string,
    data: LogSavedMealRequest,
  ): Promise<LogSavedMealResponse> => {
    const response = await apiClient.post(
      `/api/v1/saved-meals/${savedMealId}/entries`,
      data,
    );

    return response.data;
  },
};
