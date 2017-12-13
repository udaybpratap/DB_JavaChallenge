package com.db.awmd.challenge.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.repository.AccountsRepository;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AccountsService {

	@Getter
	private final AccountsRepository accountsRepository;

	@Getter
	private final NotificationService notifier;

	@Autowired
	public AccountsService(AccountsRepository accountsRepository, NotificationService notifier) {
		this.accountsRepository = accountsRepository;
		this.notifier = notifier;
	}

	public void createAccount(Account account) {
		this.accountsRepository.createAccount(account);
	}

	public Account getAccount(String accountId) {
		return this.accountsRepository.getAccount(accountId);
	}

	/*
	 * Uday Pratap: Transfer amount API
	 * 
	 * @return: boolean value after successful of transfer
	 * 
	 */

	@SneakyThrows
	public boolean transferAmount(String fromAccountId, String toAccountId, BigDecimal transferAmount) {
		Account firstLock, secondLock;

		if (fromAccountId.equalsIgnoreCase(toAccountId)) {
			throw new Exception("Cannot transfer from account to itself");
		}

		Account fromAccount = this.accountsRepository.getAccount(fromAccountId);
		Account toAccount = this.accountsRepository.getAccount(toAccountId);
		// From account should present
		if (fromAccount == null) {
			throw new Exception("From account " + fromAccountId + " does not exist!");
		}
		// To account should present
		if (toAccount == null) {
			throw new Exception("To account " + toAccountId + " does not exist!");
		}

		if (transferAmount.compareTo(BigDecimal.ZERO) < 0) {
			throw new Exception("Transfer amount should be positive.");
		}

		if (fromAccount.getAccountId().compareToIgnoreCase(toAccount.getAccountId()) > 0) {
			firstLock = fromAccount;
			secondLock = toAccount;
		} else {
			firstLock = toAccount;
			secondLock = fromAccount;
		}

		// Synchronized the block to work on concurrent operation
		synchronized (firstLock) {
			synchronized (secondLock) {
				// Account should have sufficient balance and should not be
				// negative after deduction of amount
				if (fromAccount.hasSufficientBalance(transferAmount)) {
					fromAccount.withdraw(transferAmount);
					toAccount.deposit(transferAmount);

					// Update fromAccount and toAccount in repository
					// this.accountsRepository.updateAccount(fromAccount);
					// this.accountsRepository.updateAccount(toAccount);

					// Notify the transfer to both account holder
					notifier.notifyAboutTransfer(fromAccount,
							transferAmount + " has been transferd to account " + toAccount.getAccountId());
					notifier.notifyAboutTransfer(toAccount,
							transferAmount + " has been received from account " + fromAccount.getAccountId());
				} else {
					log.info("From account have insufficient balance. From account " + fromAccountId
							+ " should not end up with negtive balance : balance - " + fromAccount.getBalance());
					throw new Exception("From account have insufficient balance. From account " + fromAccountId
							+ " should not end up with negtive balance");
				}
			}
		}
		return true;
	}

	/*
	 * public Map<String,Account> getAllAccount() { return
	 * this.accountsRepository.getAllAccount(); }
	 */

}
