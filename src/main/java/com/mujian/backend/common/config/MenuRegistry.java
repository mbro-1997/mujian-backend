package com.mujian.backend.common.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
public class MenuRegistry {
  private final Set<MenuRegistration> pages;

  public MenuRegistry(List<MenuRegistration> registrations) {
    HashSet<String> paths = new HashSet<String>();
    for (MenuRegistration page : registrations) {
      if (!page.routePath().matches("(/[a-z][a-z0-9-]*)+")
          || !page.componentPath().matches("[A-Za-z][A-Za-z0-9]*(/[A-Za-z][A-Za-z0-9]*)+")
          || !paths.add(page.routePath())) {
        throw new IllegalArgumentException("菜单注册路径不安全或重复");
      }
    }
    pages = Set.copyOf(registrations);
  }

  /** 判断给定路由是否已登记。 */
  public boolean contains(String route, String component) {
    return pages.contains(new MenuRegistration(route, component));
  }

  @Configuration
  static class ApprovedPages {
    /** 登记后台菜单 /workbench/dashboard 对应的页面组件。 */
    @Bean MenuRegistration d7Dashboard() {return new MenuRegistration("/workbench/dashboard","workbench/Dashboard");}
    /** 登记后台菜单 /workbench/cases 对应的页面组件。 */
    @Bean MenuRegistration d7Cases() {return new MenuRegistration("/workbench/cases","workbench/Cases");}
    /** 登记后台菜单 /system/accounts 对应的页面组件。 */
    @Bean MenuRegistration d7Accounts() {return new MenuRegistration("/system/accounts","system/Accounts");}
    /** 登记后台菜单 /system/role-grants 对应的页面组件。 */
    @Bean MenuRegistration d7RoleGrants() {return new MenuRegistration("/system/role-grants","system/RoleGrants");}
    /** 登记后台菜单 /system/rules 对应的页面组件。 */
    @Bean MenuRegistration d7Rules() {return new MenuRegistration("/system/rules","system/Rules");}
    /** 登记后台菜单 /system/audits 对应的页面组件。 */
    @Bean MenuRegistration d7Audits() {return new MenuRegistration("/system/audits","system/Audits");}
    /** 登记后台菜单 /accounts/users 对应的页面组件。 */
    @Bean MenuRegistration d7Users() {return new MenuRegistration("/accounts/users","accounts/Users");}
    /** 注册券模板列表。 */
    @Bean
    MenuRegistration couponTemplatesPage() { return new MenuRegistration("/operations/templates","operations/Templates"); }
    /** 注册领券活动列表。 */
    @Bean
    MenuRegistration couponActivitiesPage() { return new MenuRegistration("/operations/activities","operations/Activities"); }
    /** 注册首次奖励规则列表。 */
    @Bean
    MenuRegistration couponRewardsPage() { return new MenuRegistration("/operations/rewards","operations/Rewards"); }
    /** 注册首次奖励记录列表。 */
    @Bean
    MenuRegistration couponFactsPage() { return new MenuRegistration("/operations/facts","operations/Facts"); }
    /** 注册发放历史列表。 */
    @Bean
    MenuRegistration couponBatchesPage() { return new MenuRegistration("/operations/batches","operations/Batches"); }
    /** 注册用户持券列表。 */
    @Bean
    MenuRegistration couponCouponsPage() { return new MenuRegistration("/operations/coupons","operations/Coupons"); }
    /** 在运营菜单注册主动发券入口。 */
    @Bean
    MenuRegistration couponIssue() { return new MenuRegistration("/operations/coupon-issue","operations/CouponIssue"); }

    /** 登记后台菜单 /custom/extensions 对应的页面组件。 */
    @Bean
    MenuRegistration customExtensions(){return new MenuRegistration("/custom/extensions","custom/Extensions");}
    /** 登记后台菜单 /custom/after-sales 对应的页面组件。 */
    @Bean
    MenuRegistration customAfterSales(){return new MenuRegistration("/custom/after-sales","custom/AfterSales");}
    /** 登记后台菜单 /fulfillment/expenses 对应的页面组件。 */
    @Bean
    MenuRegistration expenseReviews() {
      return new MenuRegistration("/fulfillment/expenses", "fulfillment/TransportExpenses");
    }

    /** 登记后台菜单 /trading/settlements 对应的页面组件。 */
    @Bean
    MenuRegistration settlementTrace() {
      return new MenuRegistration("/trading/settlements", "trading/Settlements");
    }

    /** 登记后台菜单 /fulfillment/returns 对应的页面组件。 */
    @Bean
    MenuRegistration returnWarnings() {
      return new MenuRegistration("/fulfillment/returns", "fulfillment/ReturnWarnings");
    }

    /** 登记后台菜单 /fulfillment/issues 对应的页面组件。 */
    @Bean
    MenuRegistration orderIssueReviewPage() {
      return new MenuRegistration("/fulfillment/issues", "fulfillment/OrderIssues");
    }

    /** 登记后台菜单 /fulfillment/extensions 对应的页面组件。 */
    @Bean
    MenuRegistration extensionsPage() {
      return new MenuRegistration("/fulfillment/extensions", "fulfillment/Extensions");
    }

    /** 登记后台菜单 /fulfillment/claims 对应的页面组件。 */
    @Bean
    MenuRegistration claimsPage() {
      return new MenuRegistration("/fulfillment/claims", "fulfillment/Claims");
    }

    /** 登记后台菜单 /fulfillment/arbitrations 对应的页面组件。 */
    @Bean
    MenuRegistration arbitrationsPage() {
      return new MenuRegistration("/fulfillment/arbitrations", "fulfillment/Arbitrations");
    }

    /** 登记后台菜单 /fulfillment/receivables 对应的页面组件。 */
    @Bean
    MenuRegistration receivablesPage() {
      return new MenuRegistration("/fulfillment/receivables", "fulfillment/Receivables");
    }

    /** 登记后台菜单 /fulfillment/risks 对应的页面组件。 */
    @Bean
    MenuRegistration fulfillmentRisks() {
      return new MenuRegistration("/fulfillment/risks", "fulfillment/FulfillmentRisks");
    }

    /** 注册本批已批准的交易管理页面及其组件标识。 */
    @Bean
    MenuRegistration rentalOrders() {
      return new MenuRegistration("/trading/orders", "trading/RentalOrders");
    }

    /** 登记后台菜单 /trading/payments 对应的页面组件。 */
    @Bean
    MenuRegistration payments() {
      return new MenuRegistration("/trading/payments", "trading/Payments");
    }

    /** 登记后台菜单 /trading/refunds 对应的页面组件。 */
    @Bean
    MenuRegistration refunds() {
      return new MenuRegistration("/trading/refunds", "trading/Refunds");
    }

    /** 登记后台菜单 /trading/merchants 对应的页面组件。 */
    @Bean
    MenuRegistration merchants() {
      return new MenuRegistration("/trading/merchants", "trading/Merchants");
    }

    /** 登记后台菜单 /trading/transport 对应的页面组件。 */
    @Bean
    MenuRegistration transport() {
      return new MenuRegistration("/trading/transport", "trading/Transport");
    }

    /** 登记后台菜单 /props/reviews 对应的页面组件。 */
    @Bean
    MenuRegistration propReviewsPage() {
      return new MenuRegistration("/props/reviews", "props/PropReviews");
    }

    /** 登记后台菜单 /props/assets 对应的页面组件。 */
    @Bean
    MenuRegistration propAssets() {
      return new MenuRegistration("/props/assets", "props/PropAssets");
    }

    /** 登记后台菜单 /props/qr-exceptions 对应的页面组件。 */
    @Bean
    MenuRegistration propQrExceptions() {
      return new MenuRegistration("/props/qr-exceptions", "props/QrExceptions");
    }

    /** 登记后台菜单 /factory/invitations 对应的页面组件。 */
    @Bean
    MenuRegistration factoryInvites() {
      return new MenuRegistration("/factory/invitations", "factory/FactoryInvitations");
    }

    /** 登记后台菜单 /factory/onboarding 对应的页面组件。 */
    @Bean
    MenuRegistration factoryOnboarding() {
      return new MenuRegistration("/factory/onboarding", "factory/FactoryOnboarding");
    }

    /** 登记后台菜单 /factory/changes 对应的页面组件。 */
    @Bean
    MenuRegistration factoryChanges() {
      return new MenuRegistration("/factory/changes", "factory/FactoryChanges");
    }

    /** 登记后台菜单 /factory/profiles 对应的页面组件。 */
    @Bean
    MenuRegistration factoryProfiles() {
      return new MenuRegistration("/factory/profiles", "factory/FactoryProfiles");
    }

    /** 登记后台菜单 /realname/verifications 对应的页面组件。 */
    @Bean
    MenuRegistration realnameList() {
      return new MenuRegistration("/realname/verifications", "realname/RealnameList");
    }

    /** 登记后台菜单 /student/verifications 对应的页面组件。 */
    @Bean
    MenuRegistration studentList() {
      return new MenuRegistration("/student/verifications", "student/StudentList");
    }

    /** 登记后台菜单 /system/menus 对应的页面组件。 */
    @Bean
    MenuRegistration menuReadOnly() {
      return new MenuRegistration("/system/menus", "system/MenuReadOnly");
    }
  }
}
