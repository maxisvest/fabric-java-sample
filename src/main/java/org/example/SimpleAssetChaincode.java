package org.example;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ResponseUtils;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Create by yuyang
 * 2020/4/10 10:32
 */
public class SimpleAssetChaincode extends ChaincodeBase {

    /**
     * Init is called during chaincode instantiation to initialize any
     * data. Note that chaincode upgrade also calls this function to reset
     * or to migrate data.
     *
     * @param stub {@link ChaincodeStub} to operate proposal and ledger
     * @return response
     */
    @Override
    public Response init(ChaincodeStub stub) {
        try {
            // Get the args from the transaction proposal
            // List<String> args = stub.getStringArgs();
            // if (args.size() != 2) {
            //     newErrorResponse("Incorrect arguments. Expecting a key and a value");
            // }
            // Set up any variables or assets here by calling stub.putState()
            // We store the key and the value on the ledger
            // stub.putStringState(args.get(0), args.get(1));
            return newSuccessResponse();
        } catch (Throwable e) {
            return newErrorResponse("Failed to create asset");
        }
    }

    /**
     * Invoke is called per transaction on the chaincode. Each transaction is
     * either a 'get' or a 'set' on the asset created by Init function. The Set
     * method may create a new asset by specifying a new key-value pair.
     *
     * @param stub {@link ChaincodeStub} to operate proposal and ledger
     * @return response
     */
    @Override
    public Response invoke(ChaincodeStub stub) {
        try {
            // Extract the function and args from the transaction proposal
            String func = stub.getFunction();
            List<byte[]> params = stub.getArgs();
            List<Object> deserialize = deserialize(params.get(1), List.class);
            if (func.equals("set")) {
                // Return result as success payload
                return set(stub, deserialize);
            } else if (func.equals("get")) {
                // Return result as success payload
                return get(stub, deserialize);
            }else if (func.equals("geth")) {
                // Return result as success payload
                return geth(stub, deserialize);
            }else if (func.equals("del")) {
                // Return result as success payload
                return del(stub, deserialize);
            }
            return ResponseUtils.newErrorResponse("Invalid invoke function name. Expecting one of: [\"set\", \"get\"");
        } catch (Throwable e) {
            return ResponseUtils.newErrorResponse(e.getMessage());
        }
    }

    /**
     * get returns the value of the specified asset key
     *
     * @param stub {@link ChaincodeStub} to operate proposal and ledger
     * @param args key
     * @return value
     */
    private Response get(ChaincodeStub stub, List<Object> args) {
        if (args.size() != 1) {
            throw new RuntimeException("Incorrect arguments. Expecting a key");
        }

        byte[] value = stub.getState((String)args.get(0));
        if (value == null) {
            throw new RuntimeException("Asset not found: " + args.get(0));
        }

        return ResponseUtils.newSuccessResponse("成功", value);
    }

    private Response geth(ChaincodeStub stub, List<Object> args) {
        if (args.size() != 1) {
            throw new RuntimeException("Incorrect arguments. Expecting a key");
        }

        QueryResultsIterator<KeyModification> historyForKey = stub.getHistoryForKey((String) args.get(0));
        List<List<Object>> res = new ArrayList<>();
        for (KeyModification keyModification : historyForKey) {
            byte[] value = keyModification.getValue();
            boolean deleted = keyModification.isDeleted();
            List<Object> paramList = new ArrayList<>();
            paramList.add(deleted);
            paramList.add(deserialize(value, Object.class));
            res.add(paramList);
        }

        byte[] serialize = serialize(res);
        return ResponseUtils.newSuccessResponse("成功", serialize);
    }

    private Response del(ChaincodeStub stub, List<Object> args) {
        if (args.size() != 1) {
            throw new RuntimeException("Incorrect arguments. Expecting a key");
        }

        String s = (String)args.get(0);
        stub.delState(s);

        return ResponseUtils.newSuccessResponse("成功");
    }

    /**
     * set stores the asset (both key and value) on the ledger. If the key exists,
     * it will override the value with the new one
     *
     * @param stub {@link ChaincodeStub} to operate proposal and ledger
     * @param args key and value
     * @return value
     */
    private Response set(ChaincodeStub stub, List<Object> args) {
        if (args.size() != 2) {
            throw new RuntimeException("Incorrect arguments. Expecting a key and a value");
        }
        stub.putState((String) args.get(0), serialize(args.get(1)));
        return ResponseUtils.newSuccessResponse("成功");
    }

    public static <T> byte[] serialize(T obj){
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        HessianOutput ho = new HessianOutput(os);
        try {
            ho.writeObject(obj);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return os.toByteArray();
    }

    public static <T> T deserialize(byte[] bytes, Class<T> clazz) {
        ByteArrayInputStream is = new ByteArrayInputStream(bytes);
        HessianInput hi = new HessianInput(is);
        try {
            return (T)hi.readObject();
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        new SimpleAssetChaincode().start(args);
    }

}
