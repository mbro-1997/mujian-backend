package com.mujian.backend.common.enums;

/** D3-A固定业务编码，与机器契约保持一致。 */
public enum PlanningOperation {
  createScheme,
  updateScheme,
  deleteScheme,
  archiveScheme,
  setSchemeItem,
  removeSchemeItem,
  reorderSchemeItems,
  setSchemeShare,
  disableSchemeShare,
  exportSchemePdf,
  createChecklist,
  activateChecklist,
  setChecklistItem,
  updateChecklistItem,
  saveChecklist,
  removeChecklistItem,
  deleteChecklist,
  createAddress,
  updateAddress,
  setDefaultAddress,
  deleteAddress
}
