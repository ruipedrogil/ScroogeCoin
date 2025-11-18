import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class TxHandler {
	
	private UTXOPool utxoPool;

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
    
    
    
    
    /** 
     * Greedy Aproach
     *  Start with a copy of the current UTXOPool (the unspent outputs).
     *  UNTIL you can find at least one valid transaction:
     *  Among all currently valid transactions, choose the one with the highest fee.
     *  Add it to the result set.
     *  Update the UTXOPool:
     *   Remove its inputs.
     *   Add its outputs.
     *   Repeat (since including a tx may make other ones valid).
     *   No valid transaction found – RETURN result set
     *   Advantages:
     *   Simple and works fine for small input sets.
     *   Yields near-optimal results in most cases.
     *   Disadvantage:
     *   Not guaranteed to find the absolute maximum if there are complex dependencies (where two
     *   lower-fee txs combined allow a higher-fee one).
    */
    public Transaction[] handleTxsGreedy(Transaction[] possibleTxs) {
        Set<Transaction> result = new HashSet<>();
        UTXOPool poolCopy = new UTXOPool(this.utxoPool);

        boolean found = true;
        while(found){
            found = false;
            Transaction bestTx = null;
            double bestFee = Double.NEGATIVE_INFINITY;

            // search for max fee transaction
            for(Transaction tx : possibleTxs){
                if(result.contains(tx)) 
                	continue;
                TxHandler tempHandler = new TxHandler(poolCopy);
                if(tempHandler.isValidTx(tx)){
                    double fee = calcFee(tx, poolCopy);
                    if(fee > bestFee){
                        bestFee = fee;
                        bestTx = tx;
                    }
                }
            }

            if(bestTx != null){
                result.add(bestTx);
                found = true;

                for(Transaction.Input in : bestTx.getInputs()){
                    UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
                    poolCopy.removeUTXO(utxo);
                }
                for(int i = 0; i < bestTx.numOutputs(); i++){
                    UTXO utxo = new UTXO(bestTx.getHash(), i);
                    poolCopy.addUTXO(utxo, bestTx.getOutput(i));
                }
            }
        }

        return result.toArray(new Transaction[0]);
    }
    
    
    /**
     * Brute Force Search
     * optimal but expensive. 
     * Try all subsets of possibleTxs, validate each subset, and compute total fees — keep the best.
     * Complexity: 𝑂(2𝑛)— exponential, only feasible for small n 
     */
    public Transaction[] handleTxsBruteForce(Transaction[] possibleTxs) {
        Transaction[] bestSet = new Transaction[0];
        double bestFee = Double.NEGATIVE_INFINITY;

        int n = possibleTxs.length;
        for(int mask = 0; mask < (1 << n); mask++) {
            UTXOPool poolCopy = new UTXOPool(this.utxoPool);
            Set<Transaction> currentSet = new HashSet<>();
            double totalFee = 0;
            boolean validSet = true;

            for(int i = 0; i < n; i++){
                if((mask & (1 << i)) != 0){
                    Transaction tx = possibleTxs[i];
                    TxHandler tempHandler = new TxHandler(poolCopy);
                    if(tempHandler.isValidTx(tx)){
                        double fee = calcFee(tx, poolCopy); 
                        for(Transaction.Input in : tx.getInputs()){
                            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
                            poolCopy.removeUTXO(utxo);
                        }
                        for(int j = 0; j < tx.numOutputs(); j++){
                            UTXO utxo = new UTXO(tx.getHash(), j);
                            poolCopy.addUTXO(utxo, tx.getOutput(j));
                        }
                        totalFee += fee;
                        currentSet.add(tx);
                    }else {
                        validSet = false;
                        break;
                    }
                }
            }

            if(validSet && totalFee > bestFee){
                bestFee = totalFee;
                bestSet = currentSet.toArray(new Transaction[0]);
            }
        }  
        return bestSet;
    }
    
    
    
    public Transaction[] handleTxsTopoGreedy(Transaction[] possibleTxs) {
        //dependance graph
        Map<Transaction, List<Transaction>> adj = new HashMap<>();
        Map<Transaction, Integer> indegree = new HashMap<>();
        double totalFee = 0;
        for(Transaction tx : possibleTxs){
            adj.put(tx, new ArrayList<>());
            indegree.put(tx, 0);
        }
        for(Transaction tx : possibleTxs){
            for(Transaction.Input in : tx.getInputs()){
                for(Transaction other : possibleTxs){
                    if(Arrays.equals(in.prevTxHash, other.getHash())){
                        adj.get(other).add(tx);  //tx is dependant of other
                        indegree.put(tx, indegree.get(tx) + 1);
                    }
                }
            }
        }

        //Topological sort
        Queue<Transaction> q = new LinkedList<>();
        for(Transaction tx : possibleTxs){
            if (indegree.get(tx) == 0) q.add(tx);
        }
        List<Transaction> topoOrder = new ArrayList<>();
        while(!q.isEmpty()){
            Transaction cur = q.poll();
            topoOrder.add(cur);
            for(Transaction nxt : adj.get(cur)){
                indegree.put(nxt, indegree.get(nxt) - 1);
                if(indegree.get(nxt) == 0) q.add(nxt);
            }
        }

        //greedy approach
        Set<Transaction> result = new HashSet<>();
        UTXOPool poolCopy = new UTXOPool(this.utxoPool);
        for (Transaction tx : topoOrder){
            TxHandler tempHandler = new TxHandler(poolCopy);
            if (tempHandler.isValidTx(tx)){
                double fee = calcFee(tx, poolCopy);
                totalFee += fee;
                result.add(tx);

                for(Transaction.Input in : tx.getInputs()){
                    UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
                    poolCopy.removeUTXO(utxo);
                }
                for(int i = 0; i < tx.numOutputs(); i++){
                    UTXO utxo = new UTXO(tx.getHash(), i);
                    poolCopy.addUTXO(utxo, tx.getOutput(i));
                }
            }
        }
        System.out.println("\nTopo+Greedy total fees=" + totalFee);
        return result.toArray(new Transaction[0]);
    }




    // calcule transaction fee
    double calcFee(Transaction tx, UTXOPool pool){
        double inVal = 0, outVal = 0;
        for(Transaction.Input in : tx.getInputs()){
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            Transaction.Output prevOut = pool.getTxOutput(utxo);
            if (prevOut != null) {
                inVal += prevOut.value;
            }
        }
        for(Transaction.Output out : tx.getOutputs()){
            outVal += out.value;
        }
        return inVal - outVal;
    }

    
    
    
    

}
