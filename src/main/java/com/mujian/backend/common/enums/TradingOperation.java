package com.mujian.backend.common.enums;

import com.mujian.backend.common.support.D2Codes;

/** 固定交易动作及恢复时必须重新核对的身份/权限。 */
public enum TradingOperation {
  createCustomDraft(D2Codes.CODE_DEMANDER, null),
  saveCustomDraft(D2Codes.CODE_DEMANDER, null),
  submitCustomRequirement(D2Codes.CODE_DEMANDER, null),
  withdrawCustomRequirement(D2Codes.CODE_DEMANDER, null),
  requestQuoteRevision(D2Codes.CODE_DEMANDER, null),
  acceptCustomQuote(D2Codes.CODE_DEMANDER, null),
  startCustomReview(D2Codes.CODE_SUPPLIER, null),
  declineCustomRequirement(D2Codes.CODE_SUPPLIER, null),
  saveCustomQuote(D2Codes.CODE_SUPPLIER, null),
  publishCustomQuote(D2Codes.CODE_SUPPLIER, null),
  launchCustomPayment(D2Codes.CODE_DEMANDER, null),
  cancelBeforeProduction(D2Codes.CODE_DEMANDER, null),
  confirmCustomPickup(D2Codes.CODE_DEMANDER, null),
  acceptCustomDelivery(D2Codes.CODE_DEMANDER, null),
  reportCustomAfterSale(D2Codes.CODE_DEMANDER, null),
  startCustomProduction(D2Codes.CODE_SUPPLIER, null),
  readyCustomDelivery(D2Codes.CODE_SUPPLIER, null),
  dispatchCustomDelivery(D2Codes.CODE_SUPPLIER, null),
  deliverCustomOrder(D2Codes.CODE_SUPPLIER, null),
  registerCustomPickup(D2Codes.CODE_SUPPLIER, null),
  saveCustomExtension(D2Codes.CODE_SUPPLIER, null),
  submitCustomExtension(D2Codes.CODE_SUPPLIER, null),
  startAfterSaleHandling(D2Codes.CODE_SUPPLIER, null),
  decideCustomExtension(D2Codes.CODE_ADMIN, "admin:custom:extension-review"),
  submitCustomAfterSaleStatement(D2Codes.CODE_SUPPLIER, null),
  resolveCustomAfterSale(D2Codes.CODE_ADMIN, "admin:custom:after-sale-resolve"),
  requestExpiredRequote(D2Codes.CODE_DEMANDER, null),
  openArbitration(D2Codes.CODE_ADMIN,"admin:arbitration:decide"),
  draftDecision(D2Codes.CODE_ADMIN,"admin:arbitration:decide"),
  confirmDecision(D2Codes.CODE_ADMIN,"admin:arbitration:decide"),
  requestArbitrationEvidence(D2Codes.CODE_ADMIN,"admin:arbitration:decide"),
  adminArbitrationSupplement(D2Codes.CODE_ADMIN,"admin:arbitration:decide"),
  buyerArbitrationSupplement(D2Codes.CODE_DEMANDER,null),
  factoryArbitrationSupplement(D2Codes.CODE_SUPPLIER,null),
  payCompensation(D2Codes.CODE_DEMANDER,null),
  createClaim(D2Codes.CODE_SUPPLIER,null),
  saveClaim(D2Codes.CODE_SUPPLIER,null),
  submitClaim(D2Codes.CODE_SUPPLIER,null),
  withdrawClaim(D2Codes.CODE_SUPPLIER,null),
  supplementClaim(D2Codes.CODE_SUPPLIER,null),
  reviewClaim(D2Codes.CODE_ADMIN,"admin:claim:review"),
  confirmClaim(D2Codes.CODE_DEMANDER,null),
  appealClaim(D2Codes.CODE_DEMANDER,null),
  recordClaimStock(D2Codes.CODE_SUPPLIER,null),
  submitExtension(D2Codes.CODE_DEMANDER,null),
  payExtension(D2Codes.CODE_DEMANDER,null),
  cancelExtension(D2Codes.CODE_DEMANDER,null),
  approveExtension(D2Codes.CODE_SUPPLIER,null),
  rejectExtension(D2Codes.CODE_SUPPLIER,null),
  resolveOrderIssue(D2Codes.CODE_ADMIN,"admin:receipt-issue:resolve"),
  requestIssueEvidence(D2Codes.CODE_ADMIN,"admin:receipt-issue:resolve"),
  supplementOrderIssue(D2Codes.CODE_DEMANDER,null),
  submitOrderIssue(D2Codes.CODE_DEMANDER,null),
  case_contact_record(D2Codes.CODE_ADMIN, "admin:case:contact-record"),
  acknowledgeSettlement(D2Codes.CODE_ADMIN, "admin:settlement:acknowledge"),
  saveExpense(D2Codes.CODE_SUPPLIER, null),
  submitExpense(D2Codes.CODE_SUPPLIER, null),
  reviewExpense(D2Codes.CODE_ADMIN, "admin:fulfillment:expense:review"),
  saveOutbound(D2Codes.CODE_SUPPLIER, null),
  setOutboundQuantity(D2Codes.CODE_SUPPLIER, null),
  scanOutbound(D2Codes.CODE_SUPPLIER, null),
  scanReturn(D2Codes.CODE_SUPPLIER, null),
  setReturnQuantity(D2Codes.CODE_SUPPLIER, null),
  confirmTerminationReturn(D2Codes.CODE_SUPPLIER, null),
  confirmReturn(D2Codes.CODE_SUPPLIER, null),
  adminConfirmArrival(D2Codes.CODE_ADMIN, "admin:fulfillment:return:close"),
  adminCloseReturn(D2Codes.CODE_ADMIN, "admin:fulfillment:return:close"),
  saveReturnShipment(D2Codes.CODE_DEMANDER, null),
  sendReturnShipment(D2Codes.CODE_DEMANDER, null),
  confirmPickupHandover(D2Codes.CODE_SUPPLIER,null),
  assessOverdueFee(D2Codes.CODE_ADMIN,"admin:fulfillment:return:close"),
  startFactoryPickup(D2Codes.CODE_SUPPLIER, null),
  factoryAttachEvidence(D2Codes.CODE_SUPPLIER, null),
  buyerAttachEvidence(D2Codes.CODE_DEMANDER, null),
  factoryRemoveDraftEvidence(D2Codes.CODE_SUPPLIER, null),
  buyerRemoveDraftEvidence(D2Codes.CODE_DEMANDER, null),
  confirmReceipt(D2Codes.CODE_DEMANDER, null),
  confirmOutbound(D2Codes.CODE_SUPPLIER, null),
  submitCheckout(D2Codes.CODE_DEMANDER, null),
  launchCheckoutPayment(D2Codes.CODE_DEMANDER, null),
  cancelCheckoutPayment(D2Codes.CODE_DEMANDER, null),
  cancelRentalOrder(D2Codes.CODE_DEMANDER, null),
  acceptFactoryOrder(D2Codes.CODE_SUPPLIER, null),
  rejectFactoryOrder(D2Codes.CODE_SUPPLIER, null),
  startFactoryMerchantOnboarding(D2Codes.CODE_SUPPLIER, null),
  syncFactoryMerchantAccount(D2Codes.CODE_SUPPLIER, null),
  adminSyncMerchantAccount(D2Codes.CODE_ADMIN, "admin:trade:merchant:sync"),
  createVehicle(D2Codes.CODE_ADMIN, "admin:trade:transport:manage"),
  updateVehicle(D2Codes.CODE_ADMIN, "admin:trade:transport:manage"),
  createPriceRule(D2Codes.CODE_ADMIN, "admin:trade:transport:manage"),
  updatePriceRule(D2Codes.CODE_ADMIN, "admin:trade:transport:manage"),
  publishPriceRule(D2Codes.CODE_ADMIN, "admin:trade:transport:manage"),
  endPriceRule(D2Codes.CODE_ADMIN, "admin:trade:transport:manage");
  private final String role;
  private final String permission;

  TradingOperation(String role, String permission) {
    this.role = role;
    this.permission = permission;
  }

  /** 返回动作所属业务角色。 */
  public String role() {
    return role;
  }

  /** 返回管理动作精确权限，非管理动作为空。 */
  public String permission() {
    return permission;
  }
}
