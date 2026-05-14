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

/** 已登录：导出画像标签 + 分层记忆 JSON（须 Bearer）。 */
export async function exportProfileDataJson(): Promise<unknown> {
  const { data } = await http.get<unknown>("/open/v1/profile/export");
  return data;
}

/** 已登录：删除本租户下画像标签、抽象/具体记忆与设备绑定记录（须 Bearer）。 */
export async function purgeProfileData(): Promise<void> {
  await http.delete("/open/v1/profile/data");
}
