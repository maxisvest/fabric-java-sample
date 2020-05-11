package com.maxisvest.fabric.service;

import com.maxisvest.fabric.blockchain.ChaincodeManager;
import com.maxisvest.fabric.constant.Constant;
import com.maxisvest.fabric.util.FabricManager;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Create by yuyang
 * 2020/5/8 17:48
 */
@Service
public class DataService {

    private ChaincodeManager chaincodeManager;

    public DataService() throws InvalidArgumentException, NoSuchAlgorithmException, IOException, TransactionException, NoSuchProviderException, CryptoException, InvalidKeySpecException {
        chaincodeManager = FabricManager.obtain("asset", Constant.ASSET_CHAINCODE_VERSION).getManager();
    }

    public void setContent(String userID, String contentType, Object content) {
        try {
            Map<String, Object> stringObjectMap = chaincodeManager.setContent(userID, contentType, content);
            String code = (String) stringObjectMap.get("code");
            if (!code.equals("success")) {
                throw new RuntimeException("失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<List<Object>> getContents(String userID) {
        try {
            Map<String, Object> contents = chaincodeManager.getContents(userID);
            String code = (String) contents.get("code");
            if (!code.equals("success")) {
                throw new RuntimeException("失败");
            }
            Object data = contents.get(("data"));
            List<List<Object>> dataInfos = (List<List<Object>>) data;
            return dataInfos;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }


}
