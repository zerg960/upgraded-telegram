package net.kreatious.ethereum.upgradedtelegram.erc20.scenario;

import net.kreatious.ethereum.upgradedtelegram.UpgradedtelegramApplicationTests;
import net.kreatious.ethereum.upgradedtelegram.contract.generated.Token;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestContextManager;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;

import static net.kreatious.ethereum.upgradedtelegram.contract.generated.Token.load;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
@SpringBootTest
public class ApproveAllowanceAndTransferFromTest_Negative extends UpgradedtelegramApplicationTests {

    private static final Logger log = LoggerFactory.getLogger(ApproveAllowanceAndTransferFromTest_Negative.class);

    // Parameters:
    // 1. Allowance limit (in Wei equivalent)
    // 2. Tokens to transfer array (in Wei equivalent)
    @Parameterized.Parameters
    public static Collection<Object[]> tokensToTransferList() {
        return Arrays.asList(new Object[][] {
            { BigInteger.valueOf(30_000), new BigInteger[]{BigInteger.valueOf(20_000), BigInteger.valueOf(10_001), BigInteger.ZERO} },
            { BigInteger.valueOf(20_000), new BigInteger[]{BigInteger.valueOf(19_999), BigInteger.valueOf(2), BigInteger.ZERO, } }
        });
    }

    private TestContextManager testContextManager;

    private final BigInteger allowance;
    private final BigInteger[] tokensToTransferArray;

    public ApproveAllowanceAndTransferFromTest_Negative(BigInteger allowance, BigInteger... tokensToTransferArray) {

        this.allowance = allowance;
        this.tokensToTransferArray = tokensToTransferArray;
    }
    
    @Before
    public void setUp() throws Exception {

        // Setup Spring Boot context
        this.testContextManager = new TestContextManager(getClass());
        this.testContextManager.prepareTestInstance(this);
        
        super.setUp();
    }
    
    /**
     * 1. Tests approval of allowance limit by contract owner to Alice
     * 2. Tests transfer by Alice to herself on behalf of contract owner
     *
     * @throws Exception
     */
    @Test
    public void testApproveAllowanceAndTransferFromNegative() throws Exception {
    
        log.info("******************** START: Test approve allowance Negative");

        BigInteger ownerSupply = getOwnerContract().balanceOf(getOwnerAddress()).send();
        log.info(">>>>>>>>>> Owner's supply before = " + ownerSupply.toString());

        BigInteger aliceTokens = getOwnerContract().balanceOf(getAliceAddress()).send();
        log.info(">>>>>>>>>> Alice's tokens before = " + aliceTokens.toString());

        // Approve allowance limit for Alice
        TransactionReceipt approveReceipt = getOwnerContract().approve(getAliceAddress(), allowance).send();
        log.info(">>>>>>>>>> approve tx hash = " + approveReceipt.getTransactionHash());
        log.info(">>>>>>>>>> approve status = " + approveReceipt.getStatus());

        // Test that approve has succeeded
        assertEquals(approveReceipt.getStatus(), "1");

        Token.ApprovalEventResponse approvalEventValues = getOwnerContract().getApprovalEvents(approveReceipt).get(0);
        log.info(">>>>>>>>>> value from approval event = " + approvalEventValues._value);

        // Test approval event particulars
        assertThat(approvalEventValues._owner, equalTo(getOwnerAddress()));
        assertThat(approvalEventValues._spender, equalTo(getAliceAddress()));
        assertThat(approvalEventValues._value, equalTo(allowance));

        log.info(">>>>>>>>>> Alice's allowance = " + getOwnerContract().allowance(getOwnerAddress(), getAliceAddress()).send().toString());

        // Test allowance limit for Alice
        assertThat(getOwnerContract().allowance(getOwnerAddress(), getAliceAddress()).send(), equalTo(allowance));

        log.info("******************** END: Test approve allowance Negative");

        log.info("******************** START: Test transfer from Negative");

        // Test performing transfer as Alice on behalf of contract owner

        // Alice requires her own contract instance
        Credentials alice = Credentials.create(getAlicePrivateKey());
        Token aliceContract = load(getContractAddress(), getAdmin(), alice, getGasPrice(), getGasLimit());

        for (int i = 0; i < tokensToTransferArray.length; i++) {

            BigInteger tokensToTransfer = tokensToTransferArray[i];

            log.info(">>>>>>>>>> tokensToTransfer = " + tokensToTransfer.toString());

            // First value is ok...
            if (i == 0) {

                // Do transferFrom
                TransactionReceipt aliceTransferReceipt = aliceContract.transferFrom(
                        getOwnerAddress(),
                        getAliceAddress(),
                        tokensToTransfer).send();

                log.info(">>>>>>>>>> transferFrom tx hash = " + aliceTransferReceipt.getTransactionHash());
                log.info(">>>>>>>>>> transferFrom status = " + aliceTransferReceipt.getStatus());

                // Test that transfer has succeeded
                assertEquals(aliceTransferReceipt.getStatus(), "1");

                Token.TransferEventResponse aliceTransferEventValues = aliceContract.getTransferEvents(aliceTransferReceipt).get(0);
                log.info(">>>>>>>>>> value from transfer event = " + aliceTransferEventValues._value);

                // Test transfer event particulars
                assertThat(aliceTransferEventValues._from, equalTo(getOwnerAddress()));
                assertThat(aliceTransferEventValues._to, equalTo(getAliceAddress()));
                assertThat(aliceTransferEventValues._value, equalTo(tokensToTransfer));

                // Test that the owner's supply has been subtracted by the tokens transferred to Alice

                ownerSupply = ownerSupply.subtract(tokensToTransfer);

                BigInteger ownerSupplyAfter = aliceContract.balanceOf(getOwnerAddress()).send();
                log.info(">>>>>>>>>> Owner's supply after = " + ownerSupplyAfter.toString());

                assertThat(ownerSupplyAfter, equalTo(ownerSupply));

                // Test that Alice's tokens have been increased by the transferred tokens

                aliceTokens = aliceTokens.add(tokensToTransfer);

                BigInteger aliceTokensAfter = aliceContract.balanceOf(getAliceAddress()).send();
                log.info(">>>>>>>>>> Alice's tokens after = " + aliceTokensAfter.toString());

                assertThat(aliceTokensAfter, equalTo(aliceTokens));

            // ...while succeeding values are not
            } else if (i >= 1) {

                // try - catch is for testrpc
                try {

                    // Do transferFrom
                    TransactionReceipt aliceTransferReceipt = aliceContract.transferFrom(
                            getOwnerAddress(),
                            getAliceAddress(),
                            tokensToTransfer).send();

                    log.info(">>>>>>>>>> transferFrom tx hash = " + aliceTransferReceipt.getTransactionHash());
                    log.info(">>>>>>>>>> transferFrom status = " + aliceTransferReceipt.getStatus());

                    // Test that transferFrom has not succeeded
                    assertEquals(aliceTransferReceipt.getStatus(), "0");

                    // Test that no transfer event has been fired
                    assertThat("Transfer event has been fired", 0, equalTo(aliceContract.getTransferEvents(aliceTransferReceipt).size()));

                } catch (Exception e) {
                    log.error("******************** EXCEPTION = " + e.getMessage());
                }

                // Test that the owner's supply has not been subtracted by the tokens transferred to Alice

                BigInteger ownerSupplyAfter = aliceContract.balanceOf(getOwnerAddress()).send();
                log.info(">>>>>>>>>> Owner's supply after = " + ownerSupplyAfter.toString());

                assertThat(ownerSupplyAfter, equalTo(ownerSupply));

                // Test that Alice's tokens have not been increased by the transferred tokens

                BigInteger aliceTokensAfter = aliceContract.balanceOf(getAliceAddress()).send();
                log.info(">>>>>>>>>> Alice's tokens after = " + aliceTokensAfter.toString());

                assertThat(aliceTokensAfter, equalTo(aliceTokens));
            }
        }

        log.info("******************** END: Test transfer from Negative");
    }
}
