import java.util.HashSet;
import java.util.Set;

public class TxHandler {

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        
        double totInputVal = 0;
        Set<UTXO> seenUTXOs = new HashSet<>();

        for(int i = 0; i < tx.numInputs(); i++){
            Transaction.Input in = tx.getInput(i);
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);

            if(!this.utxoPool.contains(utxo))
                return false;
            
            Transaction.Output prevOut = this.utxoPool.getTxOutput(utxo);
            byte[] sig = in.signature;
            byte[] mes = tx.getRawDataToSign(i);
            
            if(!Crypto.verifySignature(prevOut.address, mes, sig))
                return false;
            
            //here we verify if the same transaction is going to be used again (double spend), the method add will return a boolean, false if the coin supposedlly not used was already used
            if(!seenUTXOs.add(utxo)){
                return false;
            }

            totInputVal += prevOut.value;
        }

        double totOutputVal = 0;
        for(Transaction.Output output : tx.getOutputs()){
            if(output.value < 0){
                return false;
            }
            totOutputVal += output.value;
        }

        //the total value from the inputs has to be bigger than the outputs value
        if(totOutputVal > totInputVal)
            return false;

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        Set<Transaction> valTxs = new HashSet<>();
        boolean change = true;

        while(change){
            change = false;
            for(Transaction tx : possibleTxs){
                if(valTxs.contains(tx))
                    continue;
                
                if(isValidTx(tx)){
                    valTxs.add(tx);
                    change = true;

                    //removed the UTXOs that this transaction spent
                    for(Transaction.Input in : tx.getInputs()){
                        UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
                        this.utxoPool.removeUTXO(utxo);
                    }

                    //added the new UTXOs that this transaction created
                    for(int i = 0; i < tx.numOutputs(); i++){
                        UTXO utxo = new UTXO(tx.getHash(), i); 
                        this.utxoPool.addUTXO(utxo, tx.getOutput(i));
                    }
                }
                
            }
        }

        Transaction[] res = new Transaction[valTxs.size()];
        int idx = 0;
        for(Transaction tx : valTxs){
            res[idx] = tx;
            idx++;
        }
        return res;        
    }

}
