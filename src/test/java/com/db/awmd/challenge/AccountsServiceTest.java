package com.db.awmd.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.DuplicateAccountIdException;
import com.db.awmd.challenge.service.AccountsService;
import com.db.awmd.challenge.service.NotificationService;

import junit.framework.Assert;

@SuppressWarnings("deprecation")
@RunWith(SpringRunner.class)
@SpringBootTest
public class AccountsServiceTest {

	@Autowired
	private AccountsService accountsService;
	@Autowired
	private NotificationService notificaionService;

	// Instance level members to avoid method level scope
	private String fromAccountId = "Id-100";
	private String toAccountId = "Id-101";

	// Setup some accounts to re-use
	@Before
	public void setUp() {
		// Reset existing accounts before each test.
		accountsService.getAccountsRepository().clearAccounts();

		Account fromAccount = new Account(fromAccountId);
		fromAccount.setBalance(new BigDecimal(1000));
		this.accountsService.createAccount(fromAccount);

		Account toAccount = new Account(toAccountId);
		toAccount.setBalance(new BigDecimal(1000));
		this.accountsService.createAccount(toAccount);
	}

	@Test
	public void addAccount() throws Exception {
		Account account = new Account("Id-123");
		account.setBalance(new BigDecimal(1000));
		this.accountsService.createAccount(account);

		assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
	}

	@Test
	public void addAccount_failsOnDuplicateId() throws Exception {
		String uniqueId = "Id-" + System.currentTimeMillis();
		Account account = new Account(uniqueId);
		this.accountsService.createAccount(account);

		try {
			this.accountsService.createAccount(account);
			fail("Should have failed when adding duplicate account");
		} catch (DuplicateAccountIdException ex) {
			assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
		}

	}

	// Test cases for transfer amounts

	// Test for invalid accounts
	@Test
	public void failsOnInvalidFromAccount() throws Exception {
		String invalidFromAccountId = "Id-12345";
		try {
			this.accountsService.transferAmount(invalidFromAccountId, toAccountId, new BigDecimal(100));
			fail("Should have failed when fromAccount is not present");
		} catch (Exception ex) {
			assertThat(ex.getMessage()).isEqualTo("From account " + invalidFromAccountId + " does not exist!");
		}
	}

	@Test
	public void failsOnInvalidToAccount() throws Exception {
		String invalidToAccountId = "Id-201";
		try {
			this.accountsService.transferAmount(fromAccountId, invalidToAccountId, new BigDecimal(100));
			fail("Should have failed when toAccount is not present");
		} catch (Exception ex) {
			assertThat(ex.getMessage()).isEqualTo("To account " + invalidToAccountId + " does not exist!");
		}
	}

	@Test
	public void failsOnSameAccountTransfer() throws Exception {
		try {
			this.accountsService.transferAmount(fromAccountId, fromAccountId, new BigDecimal(100));
			fail("Should have failed when transfer to same account");
		} catch (Exception ex) {
			assertThat(ex.getMessage()).isEqualTo("Cannot transfer from account to itself");
		}
	}

	// Test for negative amount after deduction
	@Test
	public void failsOnOverrdraft() throws Exception {
		try {
			this.accountsService.transferAmount(fromAccountId, toAccountId, new BigDecimal(1005));
			fail("Should have failed when fromAccount end up with negtive amount after deduction");
		} catch (Exception ex) {
			assertThat(ex.getMessage()).isEqualTo("From account have insufficient balance. From account "
					+ fromAccountId + " should not end up with negtive balance");
		}
	}

	// Test for negative transfer amount
	@Test
	public void failsOnNegativeTransferAmount() throws Exception {
		try {
			this.accountsService.transferAmount(fromAccountId, toAccountId, new BigDecimal(-100));
			fail("Transfer amount should be positive.");
		} catch (Exception ex) {
			assertThat(ex.getMessage()).isEqualTo("Transfer amount should be positive.");
		}
	}

	// Test for amount transfer success
	@Test
	public void transferAmount() throws Exception {
		Account fromAccount = this.accountsService.getAccount(fromAccountId);
		Account toAccount = this.accountsService.getAccount(toAccountId);

		BigDecimal fromAccountBalance = fromAccount.getBalance();
		BigDecimal toAccountBalance = toAccount.getBalance();
		BigDecimal transferAmount = new BigDecimal(90);

		assertThat(this.accountsService.transferAmount(fromAccountId, toAccountId, transferAmount));

		// Validate fromAccount has been debited and toAccount credited with
		// transfer amount
		assertThat(((fromAccount.getBalance()).compareTo(fromAccountBalance.subtract(transferAmount)) == 0)).isTrue();
		assertThat(((toAccount.getBalance()).compareTo(toAccountBalance.add(transferAmount)) == 0)).isTrue();

	}

	// Test notification service has been working
	@Test
	public void testNotificationService() throws Exception {
		Account fromAccount = this.accountsService.getAccount(fromAccountId);
		Account toAccount = this.accountsService.getAccount(toAccountId);
		try {
			this.notificaionService.notifyAboutTransfer(fromAccount,
					" 100 has been transfered to " + toAccount.getAccountId());
			this.notificaionService.notifyAboutTransfer(toAccount,
					" 100 has been received from " + fromAccount.getAccountId());
		} catch (Exception ex) {
			assertThat(false == true);
		}
	}

	@Test
	public void transferAmountConcurrentlyFromAccount() throws Exception {

		// Task to transfer from account
		Callable<Boolean> taskFrom = () -> {
			try {
				boolean status = this.accountsService.transferAmount(fromAccountId, toAccountId, new BigDecimal(5));
				TimeUnit.SECONDS.sleep(1);
				return status;
			} catch (InterruptedException e) {
				throw new IllegalStateException("task interrupted", e);
			}
		};
		testConcurrent(10, taskFrom);
	}

	@Test
	public void transferAmountConcurrentlyToAccount() throws Exception {
		// Task to transfer from reverse account
		Callable<Boolean> taskTo = () -> {
			try {
				boolean status = this.accountsService.transferAmount(toAccountId, fromAccountId, new BigDecimal(5));
				TimeUnit.SECONDS.sleep(1);
				return status;
			} catch (InterruptedException e) {
				throw new IllegalStateException("task interrupted", e);
			}
		};
		testConcurrent(10, taskTo);

	}

	private void testConcurrent(final int threadCount, Callable<Boolean> task)
			throws InterruptedException, ExecutionException {
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		try {
			// Task to call for transfer amount
			List<Callable<Boolean>> tasks = Collections.nCopies(threadCount, task);

			List<Future<Boolean>> futures = executorService.invokeAll(tasks);

			List<Boolean> resultList = new ArrayList<Boolean>(futures.size());

			// Check for exceptions
			for (Future<Boolean> future : futures) {
				// throws exception if task throw exception
				resultList.add(future.get());
			}
			// Validate the return value
			Assert.assertEquals(threadCount, futures.size());

			List<Boolean> expectedList = new ArrayList<>(threadCount);
			for (int i = 0; i < threadCount; i++) {
				expectedList.add(true);
			}

			// Validate all transfer have complete successfully
			Assert.assertEquals(expectedList, resultList);
			// Terminate executor explicitly
			executorService.shutdown();
			executorService.awaitTermination(5, TimeUnit.SECONDS);

		} catch (InterruptedException ex) {
			ex.printStackTrace();
		} finally {
			if (!executorService.isTerminated()) {
				executorService.shutdownNow();
			}
		}

	}

}