
import { apiClient } from "./client";
import type { paths } from "../types/api/veya-api";


type ExerciseSearchParams =
  paths["/api/v1/exercises"]["get"]["parameters"]["query"];

type ExercisesResponse =
  paths["/api/v1/exercises"]["get"]["responses"][200]["content"]["application/json"];

type ActivityEntriesResponse =
  paths["/api/v1/activity-entries"]["get"]["responses"][200]["content"]["application/json"];

type CreateActivityEntryRequest =
  paths["/api/v1/activity-entries"]["post"]["requestBody"]["content"]["application/json"];

type CreateActivityEntryResponse =
  paths["/api/v1/activity-entries"]["post"]["responses"][201]["content"]["application/json"];


export const workoutsApi = {

  getExercises: async (
    params?: ExerciseSearchParams,
  ): Promise<ExercisesResponse> => {
    const response = await apiClient.get("/api/v1/exercises", {
      params,
    });

    return response.data;
  },

  getActivityEntries: async (
    date: string,
  ): Promise<ActivityEntriesResponse> => {
    const response = await apiClient.get("/api/v1/activity-entries", {
      params: { date },
    });

    return response.data;
  },

  createActivityEntry: async (
    data: CreateActivityEntryRequest,
  ): Promise<CreateActivityEntryResponse> => {
    const response = await apiClient.post(
      "/api/v1/activity-entries",
      data,
    );

    return response.data;
  },
};
