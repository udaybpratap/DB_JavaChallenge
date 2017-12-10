package com.db.awmd.challenge.domain;

import java.math.BigDecimal;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TransferValueObject {

	@NotNull
	@NotEmpty
	private String fromAccountId;
	
	@NotNull
	@NotEmpty
	private String toAccountId;
	
	@NotNull
	@Min(value = 0, message = "Transfer amount must be positive.")
	private BigDecimal transferAmount;
	

	  public TransferValueObject(String fromAccountId, String toAccountId) {
	    this.fromAccountId = fromAccountId;
	    this.toAccountId = toAccountId;	    
	    this.transferAmount = BigDecimal.ZERO;
	  }

	  @JsonCreator
	  public TransferValueObject(@JsonProperty("fromAccountId") String fromAccountId,
			  @JsonProperty("toAccountId") String toAccountId,
			  @JsonProperty("balance") BigDecimal balance) {
	    this.fromAccountId = fromAccountId;
	    this.toAccountId = toAccountId;
	    this.transferAmount = balance;
	  }
}
