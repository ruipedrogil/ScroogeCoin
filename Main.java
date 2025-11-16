import java.math.BigInteger;
import java.security.*;
import java.security.spec.InvalidKeySpecException;

public class Main {
    
    public static void main(String[] args) throws Exception {
        /*
         * Generate key pairs, 
         */
        KeyPair pk_scrooge = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        KeyPair pk_alice   = KeyPairGenerator.getInstance("RSA").generateKeyPair();
      
        /*
         * Set up an initial createcoin root transaction
         *
         * Generating a root transaction tx so that Scrooge owns a coin of value 10
         * this tx will not be validated, we just need it to get a proper Transaction.Output 
         * which can be put in the UTXOPool, which will be passed to the TXHandler.
         */
        Tx tx = new Tx();
        tx.addOutput(10, pk_scrooge.getPublic());
        

        // This value has no meaning, but tx.getRawDataToSign(0) will access it in prevTxHash;
        byte[] initialHash = BigInteger.valueOf(0).toByteArray();
        tx.addInput(initialHash, 0);

        //scrooge signs it
        tx.signTx(pk_scrooge.getPrivate(), 0);

        //lets verify scrooges signature
        if (Crypto.verifySignature( tx.getOutput(0).address, tx.getRawDataToSign(0) , tx.getInput(0).signature) )
            System.out.println("True by design.  A create coin");
        else
            System.out.println("Scrooge has lost control of his keys");
        
        
        /*
         * Set up the UTXOPool
         */
        // The transaction output of the root transaction is the initial unspent output.
        UTXOPool utxoPool = new UTXOPool();
        UTXO utxo = new UTXO(tx.getHash(),0);
        utxoPool.addUTXO(utxo, tx.getOutput(0));

        /*  
         * Set up a test Transaction
         */
        Tx tx2 = new Tx();

        // the Transaction Input at position 0 has a value of 10
        tx2.addInput(tx.getHash(), 0);

        // Split the coin of value 10 into 3 coins and send all of them for simplicity to the same address (Alice)
        tx2.addOutput(5, pk_alice.getPublic());
        tx2.addOutput(3, pk_alice.getPublic());
        tx2.addOutput(2, pk_alice.getPublic());
        
        // There is only one (at position 0) Transaction.Input in tx2
        // and it contains the coin from Scrooge, therefore I have to sign with the private key from Scrooge
        tx2.signTx(pk_scrooge.getPrivate(), 0);
        
        
        TxHandler txHandler = new TxHandler(utxoPool);
        System.out.println("txHandler.isValidTx(tx2) returns: " + txHandler.isValidTx(tx2));
        Transaction list[]=new Transaction[]{tx2};
        Transaction valids[]=txHandler.handleTxs(list);
        System.out.println("txHandler.handleTxs(new Transaction[]{tx2}) returns: set with " +
            valids.length + " valid transaction(s)");
    }

    public static class Tx extends Transaction { 
        public void signTx(PrivateKey sk, int input) throws SignatureException {
            Signature sig = null;
            try {
                sig = Signature.getInstance("SHA256withRSA");
                sig.initSign(sk);
                sig.update(this.getRawDataToSign(input));
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }
            this.addSignature(sig.sign(),input);
            // Note that this method is incorrectly named,
            //it calls the hash method of the transaction class
            // hash = md.digest();
            this.finalize();
        }
    }
}