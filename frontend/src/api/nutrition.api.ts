
import { apiClient } from "./client";
import type { paths } from "../types/api/veya-api";

type FoodSearchParams =
  paths["/api/v1/foods"]["get"]["parameters"]["query"];

type FoodSearchResponse =
  paths["/api/v1/foods"]["get"]["responses"][200]["content"]["application/json"];

type FoodEntriesResponse =
  paths["/api/v1/food-entries"]["get"]["responses"][200]["content"]["application/json"];

type CreateFoodEntryRequest =
  paths["/api/v1/food-entries"]["post"]["requestBody"]["content"]["application/json"];

type CreateFoodEntryResponse =
  paths["/api/v1/food-entries"]["post"]["responses"][201]["content"]["application/json"];

type UpdateFoodEntryRequest =
  paths["/api/v1/food-entries/{entryId}"]["patch"]["requestBody"]["content"]["application/json"];

type UpdateFoodEntryResponse =
  paths["/api/v1/food-entries/{entryId}"]["patch"]["responses"][200]["content"]["application/json"];

type NutritionSummaryResponse =
  paths["/api/v1/days/{date}/nutrition"]["get"]["responses"][200]["content"]["application/json"];


export const nutritionApi = {

  searchFoods: async (
    params: FoodSearchParams,
  ): Promise<FoodSearchResponse> => {
    const response = await apiClient.get("/api/v1/foods", {
      params,
    });

    return response.data;
  },

  getFoodEntries: async (
    date: string,
  ): Promise<FoodEntriesResponse> => {
    const response = await apiClient.get("/api/v1/food-entries", {
      params: { date },
    });

    return response.data;
  },

  createFoodEntry: async (
    data: CreateFoodEntryRequest,
  ): Promise<CreateFoodEntryResponse> => {
    const response = await apiClient.post(
      "/api/v1/food-entries",
      data,
    );

    return response.data;
  },

  updateFoodEntry: async (
    entryId: string,
    data: UpdateFoodEntryRequest,
  ): Promise<UpdateFoodEntryResponse> => {
    const response = await apiClient.patch(
      `/api/v1/food-entries/${entryId}`,
      data,
    );

    return response.data;
  },

  deleteFoodEntry: async (entryId: string): Promise<void> => {
    await apiClient.delete(`/api/v1/food-entries/${entryId}`);
  },

  getNutritionSummary: async (
    date: string,
  ): Promise<NutritionSummaryResponse> => {
    const response = await apiClient.get(
      `/api/v1/days/${date}/nutrition`,
    );

    return response.data;
  },
};
