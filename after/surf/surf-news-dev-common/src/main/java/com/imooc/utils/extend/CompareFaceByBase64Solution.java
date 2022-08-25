package com.imooc.utils.extend;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.auth.ICredential;
import com.huaweicloud.sdk.core.exception.ConnectionException;
import com.huaweicloud.sdk.core.exception.RequestTimeoutException;
import com.huaweicloud.sdk.core.exception.ServiceResponseException;
import com.huaweicloud.sdk.frs.v2.FrsClient;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByBase64Response;
import com.huaweicloud.sdk.frs.v2.model.FaceCompareBase64Req;
import com.huaweicloud.sdk.frs.v2.region.FrsRegion;
import org.springframework.stereotype.Component;

/**
 * @author 高昂
 */
@Component
public class CompareFaceByBase64Solution {
    public boolean CompareFace(String face1, String face2){
        String ak = "";
        String sk = "";

        ICredential auth = new BasicCredentials()
                .withAk(ak)
                .withSk(sk);

        FrsClient client = FrsClient.newBuilder()
                .withCredential(auth)
                .withRegion(FrsRegion.valueOf("cn-north-4"))
                .build();
        CompareFaceByBase64Request request = new CompareFaceByBase64Request();
        FaceCompareBase64Req body = new FaceCompareBase64Req();
        body.withImage1Base64(face1);
        body.withImage2Base64(face2);
            request.withBody(body);
            try {
            CompareFaceByBase64Response response = client.compareFaceByBase64(request);
            if(response.getSimilarity()>0.60){
                return true;
            }else{
                return false;
            }
        } catch (ConnectionException e) {
            e.printStackTrace();
        } catch (RequestTimeoutException e) {
            e.printStackTrace();
        } catch (ServiceResponseException e) {
            e.printStackTrace();
            System.out.println(e.getHttpStatusCode());
            System.out.println(e.getErrorCode());
            System.out.println(e.getErrorMsg());
        }
        return false;
    }
}
