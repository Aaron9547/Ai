import { http } from "../plugins/http";

/** {@link com.aaron.cloud.common.profile.ProfileDeviceMergeApplicationService.MergeOutcome} */
export type ProfileMergeGuestDeviceResult = {
  conversationsReassigned: number;
  memoryChunksReassigned: number;
  ran: boolean;
};

/**
 * 已登录：将本租户下另一访客设备码的会话与画像/记忆归并到当前账号（须 Bearer）。
 * 可多次调用，合并不同未登录设备；与登录时按当前 {@code X-Device-Id} 自动归并互补。
 */
export async function mergeGuestDevice(deviceId: string): Promise<ProfileMergeGuestDeviceResult> {
  const { data } = await http.post<ProfileMergeGuestDeviceResult>("/open/v1/profile/merge-guest-device", {
    deviceId: deviceId.trim(),
  });
  return data;
}
