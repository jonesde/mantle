<!--
This software is in the public domain under CC0 1.0 Universal.

To the extent possible under law, the author(s) have dedicated all
copyright and related and neighboring rights to this software to the
public domain worldwide. This software is distributed without any
warranty.

You should have received a copy of the CC0 Public Domain Dedication
along with this software (see the LICENSE.md file). If not, see
<http://creativecommons.org/publicdomain/zero/1.0/>.
-->

<!-- See the mantle.ledger.LedgerReportServices.run#BalanceSheet service for data preparation -->

<#assign showDetail = (detail! == "true")>

<#macro showClass classInfo depth>
    <tr>
        <td style="padding-left: ${(depth-1) * 2}.3em;">${ec.l10n.localize(classInfo.className)}</td>
        <#list timePeriodIdList as timePeriodId>
            <#assign beginningClassBalance = (classInfo.balanceByTimePeriod[timePeriodId]!0) - (classInfo.postedByTimePeriod[timePeriodId]!0)>
            <td class="text-right">${ec.l10n.formatCurrency(classInfo.postedByTimePeriod[timePeriodId]!0, currencyUomId, 2)}</td>
            <td class="text-right">${ec.l10n.formatCurrency(beginningClassBalance, currencyUomId, 2)}</td>
            <td class="text-right">${ec.l10n.formatCurrency(classInfo.balanceByTimePeriod[timePeriodId]!0, currencyUomId, 2)}</td>
        </#list>
    </tr>
    <#list classInfo.glAccountInfoList! as glAccountInfo>
        <#if showDetail>
            <tr>
                <td style="padding-left: ${(depth-1) * 2 + 3}.3em;">${glAccountInfo.accountCode}: ${glAccountInfo.accountName}</td>
                <#list timePeriodIdList as timePeriodId>
                    <#assign beginningGlAccountBalance = (glAccountInfo.balanceByTimePeriod[timePeriodId]!0) - (glAccountInfo.postedByTimePeriod[timePeriodId]!0)>
                    <td class="text-right">${ec.l10n.formatCurrency(glAccountInfo.postedByTimePeriod[timePeriodId]!0, currencyUomId, 2)}</td>
                    <td class="text-right">${ec.l10n.formatCurrency(beginningGlAccountBalance, currencyUomId, 2)}</td>
                    <td class="text-right">${ec.l10n.formatCurrency(glAccountInfo.balanceByTimePeriod[timePeriodId]!0, currencyUomId, 2)}</td>
                </#list>
            </tr>
        <#else>
            <!-- ${glAccountInfo.accountCode}: ${glAccountInfo.accountName} ${glAccountInfo.balanceByTimePeriod} -->
        </#if>
    </#list>
    <#list classInfo.childClassInfoList as childClassInfo>
        <@showClass childClassInfo depth + 1/>
    </#list>
    <#if depth == 1>
        <tr class="text-info">
            <td><strong>${ec.l10n.localize(classInfo.className + " Total")}</strong></td>
            <#list timePeriodIdList as timePeriodId>
                <#assign beginningTotalBalance = (classInfo.totalBalanceByTimePeriod[timePeriodId]!0) - (classInfo.totalPostedByTimePeriod[timePeriodId]!0)>
                <td class="text-right"><strong>${ec.l10n.formatCurrency(classInfo.totalPostedByTimePeriod[timePeriodId]!0, currencyUomId, 2)}</strong></td>
                <td class="text-right"><strong>${ec.l10n.formatCurrency(beginningTotalBalance, currencyUomId, 2)}</strong></td>
                <td class="text-right"><strong>${ec.l10n.formatCurrency(classInfo.totalBalanceByTimePeriod[timePeriodId]!0, currencyUomId, 2)}</strong></td>
            </#list>
        </tr>
    </#if>
</#macro>

<table class="table table-striped table-hover table-condensed">
    <thead>
        <tr>
            <th>${ec.l10n.localize("Balance Sheet")}</th>
            <#list timePeriodIdList as timePeriodId>
                <th class="text-right">${timePeriodIdMap[timePeriodId].periodName} (${ec.l10n.localize("Closed")}: ${timePeriodIdMap[timePeriodId].isClosed}) ${ec.l10n.localize("Posted")}</th>
                <th class="text-right">${ec.l10n.localize("Beginning")}</th>
                <th class="text-right">${ec.l10n.localize("Ending")}</th>
            </#list>
        </tr>
    </thead>
    <tbody>
        <#if classInfoById.ASSET??><@showClass classInfoById.ASSET 1/></#if>
        <#if classInfoById.CONTRA_ASSET??><@showClass classInfoById.CONTRA_ASSET 1/></#if>
        <#if netAssetTotalMap??>
            <tr class="text-info" style="border-bottom: solid black;">
                <td><strong>${ec.l10n.localize("Net Asset Total")}</strong></td>
                <#list timePeriodIdList as timePeriodId>
                    <td class="text-right"><strong>${ec.l10n.formatCurrency(netAssetTotalMap.totalPosted[timePeriodId]!0, currencyUomId, 2)}</strong></td>
                    <td class="text-right"><strong>${ec.l10n.formatCurrency((netAssetTotalMap.totalBalance[timePeriodId]!0) - (netAssetTotalMap.totalPosted[timePeriodId]!0), currencyUomId, 2)}</strong></td>
                    <td class="text-right"><strong>${ec.l10n.formatCurrency(netAssetTotalMap.totalBalance[timePeriodId]!0, currencyUomId, 2)}</strong></td>
                </#list>
            </tr>
        </#if>

        <#if classInfoById.LIABILITY??><@showClass classInfoById.LIABILITY 1/></#if>
        <#if classInfoById.EQUITY??><@showClass classInfoById.EQUITY 1/></#if>
        <#if liabilityEquityTotalMap??>
            <tr class="text-info" style="border-bottom: solid black;">
                <td><strong>${ec.l10n.localize("Liability + Equity Total")}</strong></td>
                <#list timePeriodIdList as timePeriodId>
                    <td class="text-right"><strong>${ec.l10n.formatCurrency(liabilityEquityTotalMap.totalPosted[timePeriodId]!0, currencyUomId, 2)}</strong></td>
                    <td class="text-right"><strong>${ec.l10n.formatCurrency((liabilityEquityTotalMap.totalBalance[timePeriodId]!0) - (liabilityEquityTotalMap.totalPosted[timePeriodId]!0), currencyUomId, 2)}</strong></td>
                    <td class="text-right"><strong>${ec.l10n.formatCurrency(liabilityEquityTotalMap.totalBalance[timePeriodId]!0, currencyUomId, 2)}</strong></td>
                </#list>
            </tr>
        </#if>
    </tbody>
</table>
