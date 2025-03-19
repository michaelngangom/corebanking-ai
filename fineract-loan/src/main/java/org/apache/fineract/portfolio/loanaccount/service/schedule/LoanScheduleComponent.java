/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.loanaccount.service.schedule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelPeriod;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanScheduleComponent {

    public void updateLoanSchedule(Loan loan, final LoanScheduleModel modifiedLoanSchedule) {
        final List<LoanRepaymentScheduleInstallment> existingInstallments = new ArrayList<>(loan.getRepaymentScheduleInstallments());

        final List<LoanScheduleModelPeriod> periods = modifiedLoanSchedule.getPeriods();
        for (final LoanScheduleModelPeriod scheduledLoanInstallment : modifiedLoanSchedule.getPeriods()) {
            if (scheduledLoanInstallment.isRepaymentPeriod() || scheduledLoanInstallment.isDownPaymentPeriod()) {
                LoanRepaymentScheduleInstallment existingInstallment = findByInstallmentNumber(existingInstallments,
                        scheduledLoanInstallment.periodNumber());
                if (existingInstallment == null) {
                    final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan,
                            scheduledLoanInstallment.periodNumber(), scheduledLoanInstallment.periodFromDate(),
                            scheduledLoanInstallment.periodDueDate(), scheduledLoanInstallment.principalDue(),
                            scheduledLoanInstallment.interestDue(), scheduledLoanInstallment.feeChargesDue(),
                            scheduledLoanInstallment.penaltyChargesDue(), scheduledLoanInstallment.isRecalculatedInterestComponent(),
                            scheduledLoanInstallment.getLoanCompoundingDetails(), scheduledLoanInstallment.rescheduleInterestPortion(),
                            scheduledLoanInstallment.isDownPaymentPeriod());
                    loan.addLoanRepaymentScheduleInstallment(installment);
                } else {
                    existingInstallment.setFromDate(scheduledLoanInstallment.periodFromDate());
                    existingInstallment.setDueDate(scheduledLoanInstallment.periodDueDate());
                    existingInstallment.setPrincipal(scheduledLoanInstallment.principalDue());
                    existingInstallment.setInterestCharged(scheduledLoanInstallment.interestDue());
                    existingInstallment.setFeeChargesCharged(scheduledLoanInstallment.feeChargesDue());
                    existingInstallment.setPenaltyCharges(scheduledLoanInstallment.penaltyChargesDue());

                    existingInstallment.setRecalculatedInterestComponent(scheduledLoanInstallment.isRecalculatedInterestComponent());
                    existingInstallment.setLoanCompoundingDetails(scheduledLoanInstallment.getLoanCompoundingDetails());
                    existingInstallment.setRescheduleInterestPortion(scheduledLoanInstallment.rescheduleInterestPortion());
                    existingInstallment.setDownPayment(scheduledLoanInstallment.isDownPaymentPeriod());
                }
            }
        }
        // Review Installments removed
        List<LoanRepaymentScheduleInstallment> removeInstallments = new ArrayList<>();
        for (final LoanRepaymentScheduleInstallment existingInstallment : existingInstallments) {
            if (findByInstallmentNumber(periods, existingInstallment.getInstallmentNumber()) == null) {
                removeInstallments.add(existingInstallment);
            }
        }
        if (!removeInstallments.isEmpty()) {
            existingInstallments.removeAll(removeInstallments);
        }

        loan.updateLoanScheduleDependentDerivedFields();
        loan.updateLoanSummaryDerivedFields();
    }

    public void updateLoanSchedule(Loan loan, final Collection<LoanRepaymentScheduleInstallment> installments) {
        final List<LoanRepaymentScheduleInstallment> existingInstallments = new ArrayList<>(loan.getRepaymentScheduleInstallments());
        final MonetaryCurrency currency = loan.getCurrency();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            LoanRepaymentScheduleInstallment existingInstallment = findByInstallmentNumber(existingInstallments,
                    installment.getInstallmentNumber());
            if (existingInstallment != null) {
                existingInstallment.setFromDate(installment.getFromDate());
                existingInstallment.setDueDate(installment.getDueDate());
                existingInstallment.setPrincipal(installment.getPrincipal(currency).getAmount());
                existingInstallment.setInterestCharged(installment.getInterestCharged(currency).getAmount());
                existingInstallment.setFeeChargesCharged(installment.getFeeChargesCharged(currency).getAmount());
                existingInstallment.setPenaltyCharges(installment.getPenaltyChargesCharged(currency).getAmount());

                existingInstallment.setRecalculatedInterestComponent(installment.isRecalculatedInterestComponent());
                existingInstallment.setLoanCompoundingDetails(installment.getLoanCompoundingDetails());
                existingInstallment.setRescheduleInterestPortion(installment.getRescheduleInterestPortion());
                existingInstallment.setDownPayment(installment.isDownPayment());

                Set<LoanInstallmentCharge> existingCharges = existingInstallment.getInstallmentCharges();
                installment.getInstallmentCharges().addAll(existingCharges);
                existingCharges.forEach(c -> c.setInstallment(installment));
                existingInstallment.getInstallmentCharges().clear();
            } else {
                loan.addLoanRepaymentScheduleInstallment(installment);
            }
        }
        // Review Installments removed
        List<LoanRepaymentScheduleInstallment> removeInstallments = new ArrayList<>();
        for (final LoanRepaymentScheduleInstallment existingInstallment : existingInstallments) {
            if (findByInstallmentNumber(installments, existingInstallment.getInstallmentNumber()) == null) {
                removeInstallments.add(existingInstallment);
            }
        }
        if (!removeInstallments.isEmpty()) {
            existingInstallments.removeAll(removeInstallments);
        }
        loan.updateLoanScheduleDependentDerivedFields();
        loan.updateLoanSummaryDerivedFields();
    }

    private LoanRepaymentScheduleInstallment findByInstallmentNumber(final Collection<LoanRepaymentScheduleInstallment> installments,
            final Integer installmentNumber) {
        return installments.stream().filter(i -> installmentNumber.compareTo(i.getInstallmentNumber()) == 0).findFirst().orElse(null);
    }

    private LoanScheduleModelPeriod findByInstallmentNumber(final List<LoanScheduleModelPeriod> periods, final Integer installmentNumber) {
        return periods.stream().filter(p -> p.periodNumber() != null && installmentNumber.compareTo(p.periodNumber()) == 0).findFirst()
                .orElse(null);
    }

}
