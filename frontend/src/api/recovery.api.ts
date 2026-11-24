
import { apiClient } from "./client";
import type { paths } from "../types/api/veya-api";


type CreateRecoveryProposalRequest =
  NonNullable<
    paths["/api/v1/plans/{planId}/recovery-proposals"]["post"]["requestBody"]
  >["content"]["application/json"];

type CreateRecoveryProposalResponse =
  paths["/api/v1/plans/{planId}/recovery-proposals"]["post"]["responses"][201]["content"]["application/json"];

type RecoveryProposalResponse =
  paths["/api/v1/recovery-proposals/{proposalId}"]["get"]["responses"][200]["content"]["application/json"];

type DecideRecoveryProposalRequest =
  paths["/api/v1/recovery-proposals/{proposalId}/decision"]["post"]["requestBody"]["content"]["application/json"];

type DecideRecoveryProposalResponse =
  paths["/api/v1/recovery-proposals/{proposalId}/decision"]["post"]["responses"][200]["content"]["application/json"];

type OpenDeviationsResponse =
  paths["/api/v1/plans/{planId}/deviations"]["get"]["responses"][200]["content"]["application/json"];


export const recoveryApi = {

  createRecoveryProposal: async (
    planId: string,
    data?: CreateRecoveryProposalRequest,
  ): Promise<CreateRecoveryProposalResponse> => {
    const response = await apiClient.post(
      `/api/v1/plans/${planId}/recovery-proposals`,
      data ?? {},
    );

    return response.data;
  },

  getRecoveryProposal: async (
    proposalId: string,
  ): Promise<RecoveryProposalResponse> => {
    const response = await apiClient.get(
      `/api/v1/recovery-proposals/${proposalId}`,
    );

    return response.data;
  },

  decideRecoveryProposal: async (
    proposalId: string,
    data: DecideRecoveryProposalRequest,
  ): Promise<DecideRecoveryProposalResponse> => {
    const response = await apiClient.post(
      `/api/v1/recovery-proposals/${proposalId}/decision`,
      data,
    );

    return response.data;
  },

  getOpenDeviations: async (
    planId: string,
  ): Promise<OpenDeviationsResponse> => {
    const response = await apiClient.get(
      `/api/v1/plans/${planId}/deviations`,
    );

    return response.data;
  },
};
