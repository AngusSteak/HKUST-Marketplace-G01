# Unit test report

Command: `mvnw.cmd test` (JUnit 5, Maven Surefire)

**Result: 665 tests, 0 failures, 0 errors, 0 skipped. 100% pass.**

The suite uses a private in-memory SQLite database per test fixture, so it never reads or writes `data/marketplace.db`. The screen tests under `controllers` open the real JavaFX screens on that in-memory data; on a machine without a display they are skipped rather than failed.

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| com.hkust.marketplace.controllers.LoginAndRegisterScreensTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.admin.AdminAccountStatusTest | 3 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.admin.AdminDealDetailsTest | 2 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.admin.AdminEditListingValidationTest | 7 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.admin.AdminReviewVisibilityTest | 3 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.admin.AdminScreensTest | 9 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.buyer.BorrowPeriodParsingTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.buyer.BuyerNotificationTargetTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.buyer.BuyerScreensTest | 10 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.chat.ChatOfferLinkTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.seller.SellerDashboardMarkSoldTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.seller.SellerDashboardReviewsTest | 9 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.seller.SellerNotificationBoardTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.seller.SellerNotificationTargetTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.seller.SellerRequestChatNavigationTest | 3 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.seller.SellerScreensTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.seller.SellerStatsChartTest | 2 | 0 | 0 | 0 |
| com.hkust.marketplace.controllers.seller.SellerStatsOutcomeTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.forms.ListingFormStateBranchTest | 10 | 0 | 0 | 0 |
| com.hkust.marketplace.forms.ListingFormStateTest | 20 | 0 | 0 | 0 |
| com.hkust.marketplace.models.CategoryLabelTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.models.ConditionLabelTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.models.DealLifecycleTest | 36 | 0 | 0 | 0 |
| com.hkust.marketplace.models.DealRequestTest | 13 | 0 | 0 | 0 |
| com.hkust.marketplace.models.DealTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.models.LendingDurationTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.models.LendingRulesTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.models.ListingEditabilityTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.models.ListingModelTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.models.ListingStatusTest | 2 | 0 | 0 | 0 |
| com.hkust.marketplace.models.ListingValidationTest | 3 | 0 | 0 | 0 |
| com.hkust.marketplace.models.LocationLabelTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.models.MoneyTest | 8 | 0 | 0 | 0 |
| com.hkust.marketplace.models.UserNameWithUsernameTest | 3 | 0 | 0 | 0 |
| com.hkust.marketplace.persistence.DatabaseTest | 8 | 0 | 0 | 0 |
| com.hkust.marketplace.persistence.SingleInstanceTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.AdminBlock2Test | 16 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.AdminFiltersTest | 9 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.AdminServiceBranchTest | 26 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.AuditStoreTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.BoardPersistenceTest | 7 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ChatNotificationTest | 8 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ChatServiceTest | 37 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ChatStorePersistenceTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.DealHistorySearchTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.DealRequestServiceTest | 11 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.DealStorePersistenceTest | 7 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.DealStoreTest | 7 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.DeletedFlagPersistenceTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.DeletedVisibilityTest | 3 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.DerivedSellerRatingTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.DummyDataServiceDatabaseTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.DummyDataServiceTest | 2 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.DurationTextTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.FavouriteStoreTest | 10 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ListingDetailDialogReviewsTest | 1 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ListingPhotoPersistenceTest | 1 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ListingSearchTest | 13 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ListingStoreIdTimingTest | 3 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ListingStorePersistenceTest | 8 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ListingStoreSeedTest | 1 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.LoanTimerTest | 8 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.LocalPhotoStorageRulesTest | 3 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.LocalPhotoStorageTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.LoginGateTest | 8 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.MyDealsServiceTest | 11 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.NotificationCategoryTest | 7 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.PasswordHasherTest | 2 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.PasswordRulesTest | 12 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.PhotoStorageContractTest | 2 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ProfileServiceBranchTest | 7 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ProfileServiceTest | 18 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.RegistrationServiceTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ReportStoreTest | 7 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ReviewServiceBranchTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.ReviewServiceTest | 12 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.RoleChecksTest | 8 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SeededUserSubtypesTest | 2 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerAcceptRequestTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerCounterRequestTest | 7 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerDeclineRequestTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerDeleteTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerEditTest | 9 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerFilteredRequestsTest | 3 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerMarkSoldTest | 9 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerProfileServiceTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerRequestListingTest | 6 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerReviewReplyTest | 5 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerServiceCreateListingTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SellerStatsServiceTest | 7 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SessionManagerTest | 4 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.SoftDeleteTest | 7 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.StoreAndFilterBranchTest | 8 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.UserRegistrationSubtypeTest | 3 | 0 | 0 | 0 |
| com.hkust.marketplace.utils.UserStorePersistenceTest | 5 | 0 | 0 | 0 |
| **Total** | **665** | **0** | **0** | **0** |

Raw Surefire output is regenerated by `mvnw.cmd test` under `target/surefire-reports/`.
