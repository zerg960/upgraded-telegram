package net.kreatious.ethereum.upgradedtelegram.extensions.scenario;

import net.kreatious.ethereum.upgradedtelegram.UpgradedtelegramApplicationTests;
import net.kreatious.ethereum.upgradedtelegram.contract.generated.Token;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;

import static net.kreatious.ethereum.upgradedtelegram.contract.generated.Token.load;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)  // Purchase first then sell
public class PurchaseAndSellTest extends UpgradedtelegramApplicationTests {
    
    private final Logger log = LoggerFactory.getLogger(PurchaseAndSellTest.class);
    
    @Value("${tokensPerWei}")
    private String tokensPerWeiProp;
    
    /**
     * Tests purchase of tokens by Alice
     *
     * @throws Exception
     */
    @Test
    public void test1Purchase() throws Exception {
        
        log.info("******************** START: test1Purchase()");
        
        BigInteger ownerSupply = getOwnerContract().balanceOf(getOwnerAddress()).send();
        log.info(">>>>>>>>>> Owner's supply before = " + ownerSupply.toString());
        
        BigInteger aliceTokens = getOwnerContract().balanceOf(getAliceAddress()).send();
        log.info(">>>>>>>>>> Alice's tokens before = " + aliceTokens.toString());

        // Alice requires her own contract instance
        Credentials alice = Credentials.create(getAlicePrivateKey());
        Token aliceContract = load(getContractAddress(), getAdmin(), alice, getGasPrice(), getGasLimit());
        
        BigInteger tokensPerWei = BigInteger.valueOf(Long.parseLong(tokensPerWeiProp));
        BigInteger weiToPurchase = BigInteger.valueOf(1_000_000);  // In Wei equivalent. In testnet, watch out if Alice has sufficient Ether to purchase and pay gas
        
        BigInteger totalTokensToPurchase = weiToPurchase.multiply(tokensPerWei);
        log.info(">>>>>>>>>> totalTokensToPurchase = " + totalTokensToPurchase.toString());

        // Test first if Alice has sufficient Ether to purchase the number of tokens

        EthGetBalance ethGetBalance = getAdmin().ethGetBalance(getAliceAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();
        log.info(">>>>>>>>>> Alice's Ether balance in Wei = " + ethGetBalance.getBalance().toString());
        log.info(">>>>>>>>>> Tokens to purchase in Wei = " + weiToPurchase.toString());

        assertThat("Alice has insufficient Ether to purchase the tokens", ethGetBalance.getBalance(), greaterThanOrEqualTo(weiToPurchase));

        // Do purchase
        TransactionReceipt transactionReceipt = aliceContract.purchase(weiToPurchase).send();
        log.info(">>>>>>>>>> purcahse tx hash = " + transactionReceipt.getTransactionHash());
        log.info(">>>>>>>>>> purchase status = " + transactionReceipt.getStatus());

        // Test that transfer has succeeded
        assertEquals(transactionReceipt.getStatus(), "1");

        Token.TransferEventResponse transferEventValues = aliceContract.getTransferEvents(transactionReceipt).get(0);
        log.info(">>>>>>>>>> value from transfer event = " + transferEventValues._value);
        
        // Test transfer event particulars
        assertThat(transferEventValues._from, equalTo(getOwnerAddress()));
        assertThat(transferEventValues._to, equalTo(getAliceAddress()));
        assertThat(transferEventValues._value, equalTo(totalTokensToPurchase));

        // Test that the owner's supply has been subtracted by the tokens purchased by Alice
        
        ownerSupply = ownerSupply.subtract(totalTokensToPurchase);

        BigInteger ownerSupplyAfter = aliceContract.balanceOf(getOwnerAddress()).send();
        log.info(">>>>>>>>>> Owner's supply after = " + ownerSupplyAfter.toString());
        
        assertThat(ownerSupplyAfter, equalTo(ownerSupply));

        // Test that Alice's tokens have been increased by the purchased tokens
        
        aliceTokens = aliceTokens.add(totalTokensToPurchase);
        
        BigInteger aliceTokensAfter = aliceContract.balanceOf(getAliceAddress()).send();
        log.info(">>>>>>>>>> Alice's tokens after = " + aliceTokensAfter.toString());
        
        assertThat(aliceTokensAfter, equalTo(aliceTokens));
        
        log.info("******************** END: test1Purchase()");
    }

    /**
     * Tests selling of tokens by Alice
     *
     * @throws Exception
     */
    @Test
    public void test2Sell() throws Exception {

        log.info("******************** START: test2Sell()");

        BigInteger ownerSupply = getOwnerContract().balanceOf(getOwnerAddress()).send();
        log.info(">>>>>>>>>> Owner's supply before = " + ownerSupply.toString());

        BigInteger aliceTokens = getOwnerContract().balanceOf(getAliceAddress()).send();
        log.info(">>>>>>>>>> Alice's tokens before = " + aliceTokens.toString());

        // Alice requires her own contract instance
        Credentials alice = Credentials.create(getAlicePrivateKey());
        Token aliceContract = load(getContractAddress(), getAdmin(), alice, getGasPrice(), getGasLimit());

        BigInteger tokensPerWei = BigInteger.valueOf(Long.parseLong(tokensPerWeiProp));
        BigInteger weiToSell = BigInteger.valueOf(1_000_000);  // In Wei equivalent

        BigInteger totalTokensToSell = weiToSell.multiply(tokensPerWei);
        log.info(">>>>>>>>>> totalTokensToSell = " + totalTokensToSell.toString());

        // Test first if Alice has capacity to sell the number of tokens
        assertThat("Tokens to sell are greater than Alice's balance", aliceTokens, greaterThanOrEqualTo(totalTokensToSell));

        // Do sell
        TransactionReceipt transactionReceipt = aliceContract.sell(totalTokensToSell).send();
        log.info(">>>>>>>>>> sell tx hash = " + transactionReceipt.getTransactionHash());
        log.info(">>>>>>>>>> sell status = " + transactionReceipt.getStatus());

        // Test that transfer has succeeded
        assertEquals(transactionReceipt.getStatus(), "1");

        Token.TransferEventResponse transferEventValues = aliceContract.getTransferEvents(transactionReceipt).get(0);
        log.info(">>>>>>>>>> value from transfer event = " + transferEventValues._value);

        // Test transfer event particulars
        assertThat(transferEventValues._from, equalTo(getAliceAddress()));
        assertThat(transferEventValues._to, equalTo(getOwnerAddress()));
        assertThat(transferEventValues._value, equalTo(totalTokensToSell));

        // Test that the owner's supply has been increased by the tokens sold by Alice

        ownerSupply = ownerSupply.add(totalTokensToSell);

        BigInteger ownerSupplyAfter = aliceContract.balanceOf(getOwnerAddress()).send();
        log.info(">>>>>>>>>> Owner's supply after = " + ownerSupplyAfter.toString());

        assertThat(ownerSupplyAfter, equalTo(ownerSupply));

        // Test that Alice's tokens have been subtracted by the sold tokens

        aliceTokens = aliceTokens.subtract(totalTokensToSell);

        BigInteger aliceTokensAfter = aliceContract.balanceOf(getAliceAddress()).send();
        log.info(">>>>>>>>>> Alice's tokens after = " + aliceTokensAfter.toString());

        assertThat(aliceTokensAfter, equalTo(aliceTokens));

        log.info("******************** END: test2Sell()");
    }

    /**
     * Tests selling by John who has not purchased any tokens
     *
     * @throws Exception
     */
    @Test
    public void test3SellButNotPurchasedFirst() throws Exception {

        log.info("******************** START: test3SellButNotPurchasedFirst()");

        // John requires his own contract instance
        Credentials john = Credentials.create(getJohnPrivateKey());
        Token johnContract = load(getContractAddress(), getAdmin(), john, getGasPrice(), getGasLimit());

        BigInteger tokensPerWei = BigInteger.valueOf(Long.parseLong(tokensPerWeiProp));
        BigInteger weiToSell = BigInteger.valueOf(1_000_000);  // In Wei equivalent

        BigInteger totalTokensToSell = weiToSell.multiply(tokensPerWei);
        log.info(">>>>>>>>>> totalTokensToSell = " + totalTokensToSell.toString());

        // Do sell
        // try - catch is for testrpc
        try {

            // Get John's Ether balance
            EthGetBalance ethGetBalance = getAdmin().ethGetBalance(getJohnAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();
            BigInteger johnBalance = ethGetBalance.getBalance();
            log.info(">>>>>>>>>> John's Ether balance in Wei before sell = " + johnBalance.toString());

            TransactionReceipt transactionReceipt = johnContract.sell(totalTokensToSell).send();
            log.info(">>>>>>>>>> sell tx hash = " + transactionReceipt.getTransactionHash());
            log.info(">>>>>>>>>> sell status = " + transactionReceipt.getStatus());

            // Test that sell has not succeeded
            assertEquals(transactionReceipt.getStatus(), "0");

            // Test that no transfer event has been fired
            assertThat("Transfer event has been fired", 0, equalTo(johnContract.getTransferEvents(transactionReceipt).size()));

            // Get transaction fee
            BigInteger gasUsed = transactionReceipt.getGasUsed();
            BigInteger gasPrice = getAdmin().ethGetTransactionByHash(transactionReceipt.getTransactionHash()).sendAsync().get().getTransaction().get().getGasPrice();
            BigInteger totalTxFee = gasUsed.multiply(gasPrice);

            log.info(">>>>>>>>>> sell gas used = " + gasUsed.toString());
            log.info(">>>>>>>>>> sell gas price = " + gasPrice.toString());
            log.info(">>>>>>>>>> sell total tx fee = " + totalTxFee.toString());

            // Test that John's Ether balance has (only) been subtracted by the gas fee

            johnBalance = johnBalance.subtract(totalTxFee);
            log.info(">>>>>>>>>> johnBalance minus totalTxFee = " + johnBalance.toString());

            EthGetBalance ethGetBalanceAfter = getAdmin().ethGetBalance(getJohnAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();
            BigInteger johnBalanceAfter = ethGetBalanceAfter.getBalance();
            log.info(">>>>>>>>>> John's Ether balance in Wei after sell = " + johnBalanceAfter.toString());

            assertThat(johnBalanceAfter, equalTo(johnBalance));

        } catch (Exception e) {
            log.error("******************** EXCEPTION = " + e.getMessage());
        }

        log.info("******************** END: test3SellButNotPurchasedFirst()");
    }

    /**
     * Tests selling of amount not divisible by tokensPerWei
     *
     * @throws Exception
     */
    @Test
    public void test4SellNotDivisible() throws Exception {

        log.info("******************** START: test4SellNotDivisible()");

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Purchase tokens first
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // Alice requires her own contract instance
        Credentials alice = Credentials.create(getAlicePrivateKey());
        Token aliceContract = load(getContractAddress(), getAdmin(), alice, getGasPrice(), getGasLimit());

        BigInteger tokensPerWei = BigInteger.valueOf(Long.parseLong(tokensPerWeiProp));
        BigInteger weiToPurchase = BigInteger.valueOf(1_000);  // In Wei equivalent. In testnet, watch out if Alice has sufficient Ether to purchase and pay gas

        BigInteger totalTokensToPurchase = weiToPurchase.multiply(tokensPerWei);
        log.info(">>>>>>>>>> totalTokensToPurchase = " + totalTokensToPurchase.toString());

        // Test first if Alice has sufficient Ether to purchase the number of tokens

        EthGetBalance ethGetBalance = getAdmin().ethGetBalance(getAliceAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();
        log.info(">>>>>>>>>> Alice's Ether balance in Wei = " + ethGetBalance.getBalance().toString());
        log.info(">>>>>>>>>> Tokens to purchase in Wei = " + weiToPurchase.toString());

        assertThat("Alice has insufficient Ether to purchase the tokens", ethGetBalance.getBalance(), greaterThanOrEqualTo(weiToPurchase));

        // Do purchase
        TransactionReceipt purchaseReceipt = aliceContract.purchase(weiToPurchase).send();
        log.info(">>>>>>>>>> purchase tx hash = " + purchaseReceipt.getTransactionHash());
        log.info(">>>>>>>>>> purchase status = " + purchaseReceipt.getStatus());

        // Test that transfer has succeeded
        assertEquals(purchaseReceipt.getStatus(), "1");

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Now sell tokens not divisible by tokensPerWei
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        BigInteger totalTokensToSell = totalTokensToPurchase.subtract(BigInteger.valueOf(1));  // Subtract by 1
        log.info(">>>>>>>>>> totalTokensToSell = " + totalTokensToSell.toString());

        BigInteger modulo = totalTokensToSell.mod(tokensPerWei);
        log.info(">>>>>>>>>> modulo = " + modulo.toString());

        // Assert that totalTokensToSell is not divisible by tokensPerWei
        assertNotEquals(BigInteger.ZERO, modulo);

        // Do sell

        BigInteger aliceTokens = aliceContract.balanceOf(getAliceAddress()).send();
        log.info(">>>>>>>>>> Alice's tokens before = " + aliceTokens.toString());

        // try - catch is for testrpc
        try {

            // Get Alice'e Ether balance before sell
            ethGetBalance = getAdmin().ethGetBalance(getAliceAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();
            BigInteger aliceBalance = ethGetBalance.getBalance();
            log.info(">>>>>>>>>> Alice's Ether balance in Wei before sell = " + aliceBalance.toString());

            TransactionReceipt sellReceipt = aliceContract.sell(totalTokensToSell).send();
            log.info(">>>>>>>>>> sell tx hash = " + sellReceipt.getTransactionHash());
            log.info(">>>>>>>>>> sell status = " + sellReceipt.getStatus());

            // Test that sell has not succeeded
            assertEquals(sellReceipt.getStatus(), "0");

            // Test that no transfer event has been fired
            assertThat("Transfer event has been fired", 0, equalTo(aliceContract.getTransferEvents(sellReceipt).size()));

            // Get transaction fee
            BigInteger gasUsed = sellReceipt.getGasUsed();
            BigInteger gasPrice = getAdmin().ethGetTransactionByHash(sellReceipt.getTransactionHash()).sendAsync().get().getTransaction().get().getGasPrice();
            BigInteger totalTxFee = gasUsed.multiply(gasPrice);

            log.info(">>>>>>>>>> sell gas used = " + gasUsed.toString());
            log.info(">>>>>>>>>> sell gas price = " + gasPrice.toString());
            log.info(">>>>>>>>>> sell total tx fee = " + totalTxFee.toString());

            // Test that Alice's Ether balance has (only) been subtracted by the gas fee

            aliceBalance = aliceBalance.subtract(totalTxFee);
            log.info(">>>>>>>>>> aliceBalance minus totalTxFee = " + aliceBalance.toString());

            EthGetBalance ethGetBalanceAfter = getAdmin().ethGetBalance(getAliceAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();
            BigInteger aliceBalanceAfter = ethGetBalanceAfter.getBalance();
            log.info(">>>>>>>>>> Alice's Ether balance in Wei after sell = " + aliceBalanceAfter.toString());

            assertThat(aliceBalanceAfter, equalTo(aliceBalance));

        } catch (Exception e) {
            log.error("******************** EXCEPTION = " + e.getMessage());
        }

        // Test that Alice's tokens have not been subtracted by the sold tokens

        BigInteger aliceTokensAfter = aliceContract.balanceOf(getAliceAddress()).send();
        log.info(">>>>>>>>>> Alice's tokens after = " + aliceTokensAfter.toString());

        assertThat(aliceTokensAfter, equalTo(aliceTokens));

        log.info("******************** END: test4SellNotDivisible()");
    }
}
