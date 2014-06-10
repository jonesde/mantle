/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Timestamp

/* To run these make sure moqui, and mantle are in place and run:
    "gradle cleanAll load runtime/mantle/mantle-usl:test"
   Or to quick run with saved DB copy use "gradle loadSave" once then each time "gradle reloadSave runtime/mantle/mantle-usl:test"
 */

class OrderTenantAccess extends Specification {
    @Shared
    protected final static Logger logger = LoggerFactory.getLogger(OrderTenantAccess.class)
    @Shared
    ExecutionContext ec
    @Shared
    String cartOrderId = null, orderPartSeqId
    @Shared
    Map setInfoOut, shipResult
    @Shared
    long effectiveTime = System.currentTimeMillis()

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
        // set an effective date so data check works, etc
        ec.user.setEffectiveTime(new Timestamp(effectiveTime))

        ec.entity.tempSetSequencedIdPrimary("mantle.account.method.PaymentGatewayResponse", 55600, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.ledger.transaction.AcctgTrans", 55600, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.account.invoice.Invoice", 55600, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.account.payment.PaymentApplication", 55600, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.order.OrderItemBilling", 55600, 10)
    }

    def cleanupSpec() {
        ec.entity.tempResetSequencedIdPrimary("mantle.account.method.PaymentGatewayResponse")
        ec.entity.tempResetSequencedIdPrimary("mantle.ledger.transaction.AcctgTrans")
        ec.entity.tempResetSequencedIdPrimary("mantle.account.invoice.Invoice")
        ec.entity.tempResetSequencedIdPrimary("mantle.account.payment.PaymentApplication")
        ec.entity.tempResetSequencedIdPrimary("mantle.order.OrderItemBilling")
        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    def "create Sales Order"() {
        when:
        ec.user.loginUser("joe@public.com", "moqui", null)

        String productStoreId = "POPC_DEFAULT"
        EntityValue productStore = ec.entity.makeFind("mantle.product.store.ProductStore").condition("productStoreId", productStoreId).one()
        String currencyUomId = productStore.defaultCurrencyUomId
        String priceUomId = productStore.defaultCurrencyUomId
        // String defaultLocale = productStore.defaultLocale
        // String organizationPartyId = productStore.organizationPartyId
        String vendorPartyId = productStore.organizationPartyId
        String customerPartyId = ec.user.userAccount.partyId

        Map addOut1 = ec.service.sync().name("mantle.order.OrderServices.add#OrderProductQuantity")
                .parameters([orderId:cartOrderId, productId:'DEMO_TNT', quantity:3, customerPartyId:customerPartyId,
                    currencyUomId:currencyUomId, productStoreId:productStoreId]).call()

        cartOrderId = addOut1.orderId
        orderPartSeqId = addOut1.orderPartSeqId

        setInfoOut = ec.service.sync().name("mantle.order.OrderServices.set#OrderBillingShippingInfo")
                .parameters([orderId:cartOrderId, paymentMethodId:'CustJqpCc', shippingPostalContactMechId:'CustJqpAddr',
                    shippingTelecomContactMechId:'CustJqpTeln', carrierPartyId:'_NA_', shipmentMethodEnumId:'ShMthGround']).call()
        ec.service.sync().name("mantle.order.OrderServices.place#Order").parameters([orderId:cartOrderId]).call()

        ec.user.logoutUser()

        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.order.OrderHeader orderId="${cartOrderId}" entryDate="${effectiveTime}" placedDate="${effectiveTime}"
                statusId="OrderApproved" currencyUomId="USD" productStoreId="POPC_DEFAULT" grandTotal="29.97"/>

            <mantle.account.payment.Payment paymentId="${setInfoOut.paymentId}" paymentTypeEnumId="PtInvoicePayment"
                paymentMethodId="CustJqpCc" paymentMethodTypeEnumId="PmtCreditCard" orderId="${cartOrderId}"
                orderPartSeqId="01" statusId="PmntAuthorized" amount="29.97" amountUomId="USD" fromPartyId="CustJqp"
                toPartyId="ORG_BIZI_RETAIL"/>
            <mantle.account.method.PaymentGatewayResponse paymentGatewayResponseId="55600"
                paymentOperationEnumId="PgoAuthorize"
                paymentId="${setInfoOut.paymentId}" paymentMethodId="CustJqpCc" amount="29.97" amountUomId="USD"
                transactionDate="${effectiveTime}" resultSuccess="Y" resultDeclined="N" resultNsf="N"
                resultBadExpire="N" resultBadCardNumber="N"/>
            <!-- don't validate these, allow any payment gateway: paymentGatewayConfigId="TEST_APPROVE" referenceNum="TEST" -->

            <mantle.order.OrderPart orderId="${cartOrderId}" orderPartSeqId="01" vendorPartyId="ORG_BIZI_RETAIL"
                customerPartyId="CustJqp" shipmentMethodEnumId="ShMthGround" postalContactMechId="CustJqpAddr"
                telecomContactMechId="CustJqpTeln" partTotal="29.97"/>
            <mantle.order.OrderItem orderId="${cartOrderId}" orderItemSeqId="01" orderPartSeqId="01" itemTypeEnumId="ItemProduct"
                productId="DEMO_TNT" itemDescription="Demo Tenant 1 Month Subscription" quantity="3" isModifiedPrice="N"/>
        </entity-facade-xml>""").check()
        logger.info("create Sales Order data check results: " + dataCheckErrors)

        then:
        vendorPartyId == 'ORG_BIZI_RETAIL'
        customerPartyId == 'CustJqp'

        dataCheckErrors.size() == 0
    }

    def "validate Sales Order Complete"() {
        when:
        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <!-- OrderHeader status to Completed -->
            <mantle.order.OrderHeader orderId="${cartOrderId}" statusId="OrderCompleted"/>
        </entity-facade-xml>""").check()
        logger.info("validate Sales Order Complete data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "validate Invoice"() {
        when:
        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <!-- Invoice created and Finalized (status set by action in SECA rule), then Payment Received (status set by Payment application) -->
            <mantle.account.invoice.Invoice invoiceId="55600" invoiceTypeEnumId="InvoiceSales"
                fromPartyId="ORG_BIZI_RETAIL" toPartyId="CustJqp" statusId="InvoicePmtRecvd" invoiceDate="${effectiveTime}"
                description="Invoice for Order ${cartOrderId} part 01" currencyUomId="USD"/>

            <mantle.account.invoice.InvoiceItem invoiceId="55600" invoiceItemSeqId="01" itemTypeEnumId="ItemProduct"
                productId="DEMO_TNT" quantity="3" amount="9.99" description="Demo Product One-One" itemDate="${effectiveTime}"/>
            <mantle.order.OrderItemBilling orderItemBillingId="55600" orderId="${cartOrderId}" orderItemSeqId="01"
                invoiceId="55600" invoiceItemSeqId="01" assetIssuanceId="55600" quantity="3" amount="9.99"/>
        </entity-facade-xml>""").check()
        logger.info("validate Invoice data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "validate Invoice Accounting Transaction"() {
        when:
        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <!-- AcctgTrans created for Finalized Invoice -->
            <mantle.ledger.transaction.AcctgTrans acctgTransId="55600" acctgTransTypeEnumId="AttSalesInvoice"
                organizationPartyId="ORG_BIZI_RETAIL" transactionDate="${effectiveTime}" isPosted="Y"
                postedDate="${effectiveTime}" glFiscalTypeEnumId="GLFT_ACTUAL" amountUomId="USD" otherPartyId="CustJqp"
                invoiceId="55600"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="55600" acctgTransEntrySeqId="01" debitCreditFlag="C"
                amount="29.97" glAccountId="401000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"
                productId="DEMO_TNT" invoiceItemSeqId="01"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="55600" acctgTransEntrySeqId="02" debitCreditFlag="D"
                amount="29.97" glAccountTypeEnumId="ACCOUNTS_RECEIVABLE" glAccountId="120000"
                reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"/>
        </entity-facade-xml>""").check()
        logger.info("validate Invoice Accounting Transaction data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "validate Payment Accounting Transaction"() {
        when:
        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.account.payment.Payment paymentId="${setInfoOut.paymentId}" statusId="PmntDelivered"/>
            <mantle.account.payment.PaymentApplication paymentApplicationId="55600" paymentId="${setInfoOut.paymentId}"
                invoiceId="55600" amountApplied="29.97" appliedDate="${effectiveTime}"/>
            <mantle.account.method.PaymentGatewayResponse paymentGatewayResponseId="55601"
                paymentOperationEnumId="PgoCapture"
                paymentId="${setInfoOut.paymentId}" paymentMethodId="CustJqpCc" amount="29.97" amountUomId="USD"
                transactionDate="${effectiveTime}" resultSuccess="Y" resultDeclined="N" resultNsf="N"
                resultBadExpire="N" resultBadCardNumber="N"/>
            <!-- don't validate these, allow any payment gateway: paymentGatewayConfigId="TEST_APPROVE" referenceNum="TEST" -->

            <mantle.ledger.transaction.AcctgTrans acctgTransId="55601" acctgTransTypeEnumId="AttIncomingPayment"
                organizationPartyId="ORG_BIZI_RETAIL" transactionDate="${effectiveTime}" isPosted="Y"
                glFiscalTypeEnumId="GLFT_ACTUAL" amountUomId="USD" otherPartyId="CustJqp"
                paymentId="${setInfoOut.paymentId}"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="55601" acctgTransEntrySeqId="01" debitCreditFlag="C"
                amount="29.97" glAccountId="120000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="55601" acctgTransEntrySeqId="02" debitCreditFlag="D"
                amount="29.97" glAccountId="122000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"/>
        </entity-facade-xml>""").check()
        logger.info("validate Payment Accounting Transaction data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }
}