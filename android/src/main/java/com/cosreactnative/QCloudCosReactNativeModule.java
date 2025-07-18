package com.cosreactnative;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.gson.Gson;
import com.tencent.cos.xml.CosXmlBaseService;
import com.tencent.cos.xml.CosXmlService;
import com.tencent.cos.xml.CosXmlServiceConfig;
import com.tencent.cos.xml.common.COSStorageClass;
import com.tencent.cos.xml.exception.CosXmlClientException;
import com.tencent.cos.xml.exception.CosXmlServiceException;
import com.tencent.cos.xml.listener.CosXmlBooleanListener;
import com.tencent.cos.xml.listener.CosXmlResultListener;
import com.tencent.cos.xml.listener.CosXmlResultSimpleListener;
import com.tencent.cos.xml.model.CosXmlRequest;
import com.tencent.cos.xml.model.CosXmlResult;
import com.tencent.cos.xml.model.PresignedUrlRequest;
import com.tencent.cos.xml.model.bucket.DeleteBucketRequest;
import com.tencent.cos.xml.model.bucket.GetBucketAccelerateRequest;
import com.tencent.cos.xml.model.bucket.GetBucketAccelerateResult;
import com.tencent.cos.xml.model.bucket.GetBucketLocationRequest;
import com.tencent.cos.xml.model.bucket.GetBucketLocationResult;
import com.tencent.cos.xml.model.bucket.GetBucketRequest;
import com.tencent.cos.xml.model.bucket.GetBucketResult;
import com.tencent.cos.xml.model.bucket.GetBucketVersioningRequest;
import com.tencent.cos.xml.model.bucket.GetBucketVersioningResult;
import com.tencent.cos.xml.model.bucket.HeadBucketRequest;
import com.tencent.cos.xml.model.bucket.PutBucketAccelerateRequest;
import com.tencent.cos.xml.model.bucket.PutBucketRequest;
import com.tencent.cos.xml.model.bucket.PutBucketVersioningRequest;
import com.tencent.cos.xml.model.object.DeleteObjectRequest;
import com.tencent.cos.xml.model.object.GetObjectRequest;
import com.tencent.cos.xml.model.object.HeadObjectRequest;
import com.tencent.cos.xml.model.object.PutObjectRequest;
import com.tencent.cos.xml.model.service.GetServiceRequest;
import com.tencent.cos.xml.model.service.GetServiceResult;
import com.tencent.cos.xml.model.tag.AccelerateConfiguration;
import com.tencent.cos.xml.model.tag.InitiateMultipartUpload;
import com.tencent.cos.xml.model.tag.ListAllMyBuckets;
import com.tencent.cos.xml.model.tag.ListBucket;
import com.tencent.cos.xml.model.tag.LocationConstraint;
import com.tencent.cos.xml.model.tag.VersioningConfiguration;
import com.tencent.cos.xml.transfer.COSXMLDownloadTask;
import com.tencent.cos.xml.transfer.COSXMLTask;
import com.tencent.cos.xml.transfer.COSXMLUploadTask;
import com.tencent.cos.xml.transfer.InitMultipleUploadListener;
import com.tencent.cos.xml.transfer.TransferConfig;
import com.tencent.cos.xml.transfer.TransferManager;
import com.tencent.qcloud.core.auth.QCloudCredentialProvider;
import com.tencent.qcloud.core.auth.SessionQCloudCredentials;
import com.tencent.qcloud.core.auth.ShortTimeCredentialProvider;
import com.tencent.qcloud.core.logger.COSLogger;
import com.tencent.qcloud.core.logger.LogLevel;
import com.tencent.qcloud.core.logger.channel.CosLogListener;
import com.tencent.qcloud.core.task.TaskExecutors;
import com.tencent.qcloud.track.cls.ClsLifecycleCredentialProvider;
import com.tencent.qcloud.track.cls.ClsSessionCredentials;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@ReactModule(name = QCloudCosReactNativeModule.NAME)
public class QCloudCosReactNativeModule extends ReactContextBaseJavaModule {
  public static final String NAME = "QCloudCosReactNative";

  private static final String DEFAULT_KEY = "";

  private Gson gson = new Gson();

  private final ReactApplicationContext reactContext;
  private QCloudCredentialProvider qCloudCredentialProvider = null;
  private final Object fetchDnsWaitingLock = new Object();

  private final Map<String, CosXmlService> cosServices = new HashMap<>();
  private final Map<String, TransferManager> transferManagers = new HashMap<>();
  private final Map<String, COSXMLTask> taskMap = new HashMap<>();
  private final Map<String, CosLogListener> cosLogListenerMap = new HashMap<>();
  private ClsLifecycleCredentialProvider qClsLifecycleCredentialProvider = null;

  public static ThreadPoolExecutor COMMAND_EXECUTOR = null;

  // 静态配置自定义dns
  private Map<String, String[]> dnsMap = null;
  // 动态dns fetch
  private boolean initDnsFetch = false;
  // 动态dns fetch中间缓存
  private final Map<String, List<InetAddress>> fetchMapCache = new HashMap<>();

  public QCloudCosReactNativeModule(ReactApplicationContext reactContext) {
    super(reactContext);

    this.reactContext = reactContext;
    CosXmlBaseService.BRIDGE = "ReactNative";

    COMMAND_EXECUTOR = new ThreadPoolExecutor(2, 10, 5L,
      TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(Integer.MAX_VALUE));
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void initWithPlainSecret(@NonNull String secretId, @NonNull String secretKey, final Promise promise) {
    qCloudCredentialProvider = new ShortTimeCredentialProvider(
      secretId,
      secretKey,
      600
    );
    promise.resolve(null);
  }

  @ReactMethod
  public void initWithSessionCredentialCallback(final Promise promise) {
    qCloudCredentialProvider = new BridgeCredentialProvider(reactContext);
    promise.resolve(null);
  }

  @ReactMethod
  public void initWithScopeLimitCredentialCallback(final Promise promise) {
    qCloudCredentialProvider = new BridgeScopeLimitCredentialProvider(reactContext);
    promise.resolve(null);
  }

  @ReactMethod
  public void initCustomerDNS(@NonNull ReadableArray dnsArray, final Promise promise) {
    dnsMap = new HashMap<>();
    for (int i=0;i<dnsArray.size();i++){
      ReadableMap dns = dnsArray.getMap(i);
      ReadableArray ipsArray = dns.getArray("ips");
      dnsMap.put(dns.getString("domain"), ipsArray.toArrayList().toArray(new String[0]));
    }
    promise.resolve(null);
  }

  @ReactMethod
  public void initCustomerDNSFetch(final Promise promise) {
    initDnsFetch = true;
    promise.resolve(null);
  }

  @ReactMethod
  public void forceInvalidationCredential(final Promise promise) {
    if(qCloudCredentialProvider instanceof BridgeCredentialProvider){
      BridgeCredentialProvider bridgeCredentialProvider = (BridgeCredentialProvider)qCloudCredentialProvider;
      bridgeCredentialProvider.forceInvalidationCredential();
    }
    promise.resolve(null);
  }

  @ReactMethod
  public void updateSessionCredential(ReadableMap credentials, @Nullable String stsScopesArrayJson, final Promise promise) {
    if (qCloudCredentialProvider != null &&
          (qCloudCredentialProvider instanceof BridgeCredentialProvider || qCloudCredentialProvider instanceof BridgeScopeLimitCredentialProvider)) {
      SessionQCloudCredentials sessionQCloudCredentials = null;
      if (credentials.hasKey("startTime")) {
        long startTime = (long) credentials.getDouble("startTime");
        long expiredTime = (long) credentials.getDouble("expiredTime");
        sessionQCloudCredentials = new SessionQCloudCredentials(
          credentials.getString("tmpSecretId"),
          credentials.getString("tmpSecretKey"),
          credentials.getString("sessionToken"),
          startTime,
          expiredTime
        );
      } else {
        sessionQCloudCredentials = new SessionQCloudCredentials(
          credentials.getString("tmpSecretId"),
          credentials.getString("tmpSecretKey"),
          credentials.getString("sessionToken"),
          (long) credentials.getDouble("expiredTime")
        );
      }
      if(qCloudCredentialProvider instanceof BridgeCredentialProvider) {
        ((BridgeCredentialProvider) qCloudCredentialProvider).setNewCredentials(sessionQCloudCredentials);
      } else if(qCloudCredentialProvider instanceof BridgeScopeLimitCredentialProvider) {
        ((BridgeScopeLimitCredentialProvider) qCloudCredentialProvider).setNewCredentials(sessionQCloudCredentials);
      }
    }
    promise.resolve(null);
  }

  @ReactMethod
  public void setDNSFetchIps(@NonNull String domain, @Nullable ReadableArray ips, final Promise promise) {
    List<InetAddress> inetAddresses = new ArrayList<>();
    if(ips != null && ips.size() > 0){
      for (String ip : ips.toArrayList().toArray(new String[0])) {
        try {
          inetAddresses.add(InetAddress.getByName(ip));
        } catch (UnknownHostException e) {
          e.printStackTrace();
        }
      }
    }
    this.fetchMapCache.put(domain, inetAddresses);
    synchronized (fetchDnsWaitingLock) {
      fetchDnsWaitingLock.notify();
    }
    promise.resolve(null);
  }

  @ReactMethod
  public void setCloseBeacon(boolean isCloseBeacon, final Promise promise) {
    CosXmlBaseService.IS_CLOSE_REPORT = isCloseBeacon;
    promise.resolve(null);
  }

  @ReactMethod
  public void registerDefaultService(@NonNull ReadableMap config, final Promise promise) {
    CosXmlService service = buildCosXmlService(reactContext, config);
    cosServices.put(DEFAULT_KEY, service);
    promise.resolve(null);
  }

  @ReactMethod
  public void registerDefaultTransferManger(@NonNull ReadableMap config, @Nullable ReadableMap transferConfig, final Promise promise) {
    TransferManager transferManager = buildTransferManager(reactContext, config, transferConfig);
    transferManagers.put(DEFAULT_KEY, transferManager);
    promise.resolve(null);
  }

  @ReactMethod
  public void registerService(@NonNull String key, @NonNull ReadableMap config, final Promise promise) {
    if (key.isEmpty()) {
      promise.reject(new IllegalArgumentException("register key cannot be empty"));
    }
    CosXmlService service = buildCosXmlService(reactContext, config);
    cosServices.put(key, service);
    promise.resolve(null);
  }

  @ReactMethod
  public void registerTransferManger(@NonNull String key, @NonNull ReadableMap config, @Nullable ReadableMap transferConfig, final Promise promise) {
    if (key.isEmpty()) {
      promise.reject(new IllegalArgumentException("register key cannot be empty"));
    }
    TransferManager transferManager = buildTransferManager(reactContext, config, transferConfig);
    transferManagers.put(key, transferManager);
    promise.resolve(null);
  }

  @ReactMethod
  public void headObject(@NonNull String serviceKey, @NonNull String bucket, @NonNull String cosPath, @Nullable String region, @Nullable String versionId, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    HeadObjectRequest headObjectRequest = new HeadObjectRequest(
      bucket, cosPath);
    if (region != null) {
      headObjectRequest.setRegion(region);
    }
    if (versionId != null) {
      headObjectRequest.setVersionId(versionId);
    }
    setRequestCredential(credentials, headObjectRequest);
    service.headObjectAsync(headObjectRequest, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        try {
          promise.resolve(simplifyHeader(cosXmlResult.headers));
        } catch (Exception e) {
          e.printStackTrace();
          promise.reject(e);
        }
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void deleteObject(@NonNull String serviceKey, @NonNull String bucket, @NonNull String cosPath, @Nullable String region, @Nullable String versionId, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(
      bucket, cosPath);
    if (region != null) {
      deleteObjectRequest.setRegion(region);
    }
    if (versionId != null) {
      deleteObjectRequest.setVersionId(versionId);
    }
    setRequestCredential(credentials, deleteObjectRequest);
    service.deleteObjectAsync(deleteObjectRequest, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        promise.resolve(null);
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void getObjectUrl(@NonNull String serviceKey, @NonNull String bucket, @NonNull String key, @NonNull String region, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    promise.resolve(service.getObjectUrl(bucket, region, key));
  }

  @ReactMethod
  public void getPresignedUrl(@NonNull String serviceKey, @NonNull String bucket, @NonNull String cosPath, @Nullable String signValidTime,
                              @Nullable String signHost, @Nullable ReadableMap parameters,
                              @Nullable String region, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    PresignedUrlRequest presignedUrlRequest = new PresignedUrlRequest(bucket, cosPath);
    presignedUrlRequest.setRequestMethod("GET");
    if(signValidTime != null){
      presignedUrlRequest.setSignKeyTime(Integer.parseInt(signValidTime));
    }
    if(signHost != null && !Boolean.parseBoolean(signHost)){
      presignedUrlRequest.addNoSignHeader("Host");
    }
    if(parameters != null){
      Map<String, String> parametersMap = new HashMap<>();
      ReadableMapKeySetIterator iterator = parameters.keySetIterator();
      while (iterator.hasNextKey()) {
        String key = iterator.nextKey();
        parametersMap.put(key, parameters.getString(key));
      }
      presignedUrlRequest.setQueryParameters(parametersMap);
    }
    if (region != null) {
      presignedUrlRequest.setRegion(region);
    }
    setRequestCredential(credentials, presignedUrlRequest);
    TaskExecutors.COMMAND_EXECUTOR.execute(() -> {
      try {
        String urlWithSign = service.getPresignedURL(presignedUrlRequest);
        promise.resolve(urlWithSign);
      } catch (CosXmlClientException e) {
        e.printStackTrace();
        promise.reject(e);
      }
    });
  }

  @ReactMethod
  public void preBuildConnection(@NonNull String serviceKey, @NonNull String bucket, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    service.preBuildConnectionAsync(bucket, new CosXmlResultSimpleListener() {
      @Override
      public void onSuccess() {
        promise.resolve(null);
      }

      @Override
      public void onFail(CosXmlClientException e, CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void getService(@NonNull String serviceKey, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    GetServiceRequest request = new GetServiceRequest();
    setRequestCredential(credentials, request);
    service.getServiceAsync(request, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        try {
          ListAllMyBuckets listAllMyBuckets = ((GetServiceResult) cosXmlResult).listAllMyBuckets;
          Gson gson = new Gson();
          promise.resolve(gson.toJson(listAllMyBuckets));
        } catch (Exception e) {
          e.printStackTrace();
          promise.reject(e);
        }
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void getBucket(@NonNull String serviceKey, @NonNull String bucket, @Nullable String region, @Nullable String prefix, @Nullable String delimiter,
                        @Nullable String encodingType, @Nullable String marker, @Nullable String maxKeys, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    GetBucketRequest request = new GetBucketRequest(bucket);
    if (region != null) {
      request.setRegion(region);
    }
    if (prefix != null) {
      request.setPrefix(prefix);
    }
    if (delimiter != null) {
      request.setDelimiter(delimiter);
    }
    if (encodingType != null) {
      request.setEncodingType(encodingType);
    }
    if (marker != null) {
      request.setMarker(marker);
    }
    if (maxKeys != null) {
      request.setMaxKeys(Long.parseLong(maxKeys));
    }
    setRequestCredential(credentials, request);
    service.getBucketAsync(request, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        try {
          ListBucket listBucket = ((GetBucketResult) cosXmlResult).listBucket;
          Gson gson = new Gson();
          promise.resolve(gson.toJson(listBucket));
        } catch (Exception e) {
          e.printStackTrace();
          promise.reject(e);
        }
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });

  }

  @ReactMethod
  public void putBucket(@NonNull String serviceKey, @NonNull String bucket, @Nullable String region, @Nullable String enableMAZ, @Nullable String cosacl,
                        @Nullable String readAccount, @Nullable String writeAccount, @Nullable String readWriteAccount, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    PutBucketRequest request = new PutBucketRequest(bucket);
    if (region != null) {
      request.setRegion(region);
    }
    if (enableMAZ != null) {
      request.enableMAZ(Boolean.parseBoolean(enableMAZ));
    }
    if (cosacl != null) {
      request.setXCOSACL(cosacl);
    }
    if (readAccount != null) {
      request.setXCOSGrantRead(readAccount);
    }
    if (writeAccount != null) {
      request.setXCOSGrantWrite(writeAccount);
    }
    if (readWriteAccount != null) {
      request.setXCOSReadWrite(readWriteAccount);
    }
    setRequestCredential(credentials, request);
    service.putBucketAsync(request, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        promise.resolve(null);
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void headBucket(@NonNull String serviceKey, @NonNull String bucket, @Nullable String region, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    HeadBucketRequest request = new HeadBucketRequest(bucket);
    if (region != null) {
      request.setRegion(region);
    }
    setRequestCredential(credentials, request);
    service.headBucketAsync(request, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        try {
          promise.resolve(simplifyHeader(cosXmlResult.headers));
        } catch (Exception e) {
          e.printStackTrace();
          promise.reject(e);
        }
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void deleteBucket(@NonNull String serviceKey, @NonNull String bucket, @Nullable String region, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    DeleteBucketRequest request = new DeleteBucketRequest(bucket);
    if (region != null) {
      request.setRegion(region);
    }
    setRequestCredential(credentials, request);
    service.deleteBucketAsync(request, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        promise.resolve(null);
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void getBucketAccelerate(@NonNull String serviceKey, @NonNull String bucket, @Nullable String region, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    GetBucketAccelerateRequest request = new GetBucketAccelerateRequest(bucket);
    if (region != null) {
      request.setRegion(region);
    }
    setRequestCredential(credentials, request);
    service.getBucketAccelerateAsync(request, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        try {
          AccelerateConfiguration accelerateConfiguration = ((GetBucketAccelerateResult) cosXmlResult).accelerateConfiguration;
          if (accelerateConfiguration != null) {
            promise.resolve("Enabled".equals(accelerateConfiguration.status));
          } else {
            promise.resolve(false);
          }
        } catch (Exception e) {
          e.printStackTrace();
          promise.reject(e);
        }
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void putBucketAccelerate(@NonNull String serviceKey, @NonNull String bucket, @Nullable String region, @NonNull Boolean enable, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    PutBucketAccelerateRequest request = new PutBucketAccelerateRequest(bucket, enable);
    if (region != null) {
      request.setRegion(region);
    }
    setRequestCredential(credentials, request);
    service.putBucketAccelerateAsync(request, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        promise.resolve(null);
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void getBucketLocation(@NonNull String serviceKey, @NonNull String bucket, @Nullable String region, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    GetBucketLocationRequest request = new GetBucketLocationRequest(bucket);
    if (region != null) {
      request.setRegion(region);
    }
    setRequestCredential(credentials, request);
    service.getBucketLocationAsync(request, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        try {
          LocationConstraint locationConstraint = ((GetBucketLocationResult) cosXmlResult).locationConstraint;
          promise.resolve(locationConstraint.location);
        } catch (Exception e) {
          e.printStackTrace();
          promise.reject(e);
        }
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void getBucketVersioning(@NonNull String serviceKey, @NonNull String bucket, @Nullable String region, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    GetBucketVersioningRequest request = new GetBucketVersioningRequest(bucket);
    if (region != null) {
      request.setRegion(region);
    }
    setRequestCredential(credentials, request);
    service.getBucketVersioningAsync(request, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        try {
          VersioningConfiguration versioningConfiguration = ((GetBucketVersioningResult) cosXmlResult).versioningConfiguration;
          if (versioningConfiguration != null) {
            promise.resolve("Enabled".equals(versioningConfiguration.status));
          } else {
            promise.resolve(false);
          }
        } catch (Exception e) {
          e.printStackTrace();
          promise.reject(e);
        }
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void putBucketVersioning(@NonNull String serviceKey, @NonNull String bucket, @Nullable String region, @NonNull Boolean enable, @Nullable ReadableMap credentials, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    PutBucketVersioningRequest request = new PutBucketVersioningRequest(bucket);
    request.setEnableVersion(enable);
    if (region != null) {
      request.setRegion(region);
    }
    setRequestCredential(credentials, request);
    service.putBucketVersionAsync(request, new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        promise.resolve(null);
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void doesBucketExist(@NonNull String serviceKey, @NonNull String bucket, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    service.doesBucketExistAsync(bucket, new CosXmlBooleanListener() {
      @Override
      public void onSuccess(boolean b) {
        promise.resolve(b);
      }

      @Override
      public void onFail(@Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void doesObjectExist(@NonNull String serviceKey, @NonNull String bucket, @NonNull String cosPath, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    service.doesObjectExistAsync(bucket, cosPath, new CosXmlBooleanListener() {
      @Override
      public void onSuccess(boolean b) {
        promise.resolve(b);
      }

      @Override
      public void onFail(@Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (e != null) {
          e.printStackTrace();
          promise.reject(e);
        } else {
          e1.printStackTrace();
          promise.reject(e1);
        }
      }
    });
  }

  @ReactMethod
  public void enableLogcat(@NonNull Boolean enable, final Promise promise) {
    COSLogger.enableLogcat(enable);
    promise.resolve(null);
  }

  @ReactMethod
  public void enableLogFile(@NonNull Boolean enable, final Promise promise) {
    COSLogger.enableLogFile(enable);
    promise.resolve(null);
  }

  @ReactMethod
  public void addLogListener(String key, final Promise promise) {
    CosLogListener listener = entity -> {
      if (!TextUtils.isEmpty(key)) {
        runMainThread(() -> {
          WritableMap params = Arguments.createMap();
          params.putString("key", key);
          params.putString("logEntityJson", gson.toJson(entity));
          reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(Constants.COS_EMITTER_LOG_CALLBACK, params);
        });
      }
    };
    cosLogListenerMap.put(key, listener);
    COSLogger.addLogListener(listener);
    promise.resolve(null);
  }

  @ReactMethod
  public void removeLogListener(String key, final Promise promise) {
    cosLogListenerMap.remove(key);
    promise.resolve(null);
  }

  @ReactMethod
  public void setMinLevel(@NonNull int minLevel, final Promise promise) {
    COSLogger.setMinLevel(LogLevel.values()[minLevel]);
    promise.resolve(null);
  }

  @ReactMethod
  public void setLogcatMinLevel(@NonNull int minLevel, final Promise promise) {
    COSLogger.setLogcatMinLevel(LogLevel.values()[minLevel]);
    promise.resolve(null);
  }

  @ReactMethod
  public void setFileMinLevel(@NonNull int minLevel, final Promise promise) {
    COSLogger.setFileMinLevel(LogLevel.values()[minLevel]);
    promise.resolve(null);
  }

  @ReactMethod
  public void setClsMinLevel(@NonNull int minLevel, final Promise promise) {
    COSLogger.setClsMinLevel(LogLevel.values()[minLevel]);
    promise.resolve(null);
  }

  @ReactMethod
  public void setDeviceID(@NonNull String deviceID, final Promise promise) {
    COSLogger.setDeviceID(deviceID);
    promise.resolve(null);
  }

  @ReactMethod
  public void setDeviceModel(@NonNull String deviceModel, final Promise promise) {
    COSLogger.setDeviceModel(deviceModel);
    promise.resolve(null);
  }

  @ReactMethod
  public void setAppVersion(@NonNull String appVersion, final Promise promise) {
    COSLogger.setAppVersion(appVersion);
    promise.resolve(null);
  }

  @ReactMethod
  public void setExtras(@NonNull ReadableMap extras, final Promise promise) {
    Map<String, String> parametersMap = new HashMap<>();
      ReadableMapKeySetIterator iterator = extras.keySetIterator();
      while (iterator.hasNextKey()) {
        String key = iterator.nextKey();
        parametersMap.put(key, extras.getString(key));
      }
    COSLogger.setExtras(parametersMap);
    promise.resolve(null);
  }

  @ReactMethod
  public void setLogFileEncryptionKey(@NonNull ReadableArray keyArray, @NonNull ReadableArray ivArray, final Promise promise) {
    // 将 ReadableArray 转换为 byte[]
    byte[] key = convertReadableArrayToByteArray(keyArray);
    byte[] iv = convertReadableArrayToByteArray(ivArray);

    COSLogger.setLogFileEncryptionKey(key, iv);
    promise.resolve(null);
  }

  @ReactMethod
  public void getLogRootDir(final Promise promise) {
    promise.resolve(COSLogger.getLogRootDir());
  }

  @ReactMethod
  public void setCLsChannelAnonymous(@NonNull String topicId, @NonNull String endpoint, final Promise promise) {
    COSLogger.setCLsChannel(topicId, endpoint);
    promise.resolve(null);
  }

  @ReactMethod
  public void setCLsChannelStaticKey(@NonNull String topicId, @NonNull String endpoint, @NonNull String secretId, @NonNull String secretKey, final Promise promise) {
    COSLogger.setCLsChannel(topicId, endpoint, secretId, secretKey);
    promise.resolve(null);
  }

  @ReactMethod
  public void setCLsChannelSessionCredential(@NonNull String topicId, @NonNull String endpoint, final Promise promise) {
    qClsLifecycleCredentialProvider = new BridgeClsLifecycleCredentialProvider(reactContext);
    COSLogger.setCLsChannel(topicId, endpoint, qClsLifecycleCredentialProvider);
    promise.resolve(null);
  }

  @ReactMethod
  public void updateCLsChannelSessionCredential(ReadableMap credentials, final Promise promise) {
    if (qClsLifecycleCredentialProvider != null && qClsLifecycleCredentialProvider instanceof BridgeClsLifecycleCredentialProvider) {
      ClsSessionCredentials sessionQCloudCredentials = new ClsSessionCredentials(
        credentials.getString("tmpSecretId"),
        credentials.getString("tmpSecretKey"),
        credentials.getString("sessionToken"),
        (long) credentials.getDouble("expiredTime")
      );
      ((BridgeClsLifecycleCredentialProvider) qClsLifecycleCredentialProvider).setNewCredentials(sessionQCloudCredentials);
    }
    promise.resolve(null);
  }

  @ReactMethod
  public void addSensitiveRule(@NonNull String ruleName, @NonNull String regex, final Promise promise) {
    COSLogger.addSensitiveRule(ruleName, regex);
    promise.resolve(null);
  }

  @ReactMethod
  public void removeSensitiveRule(@NonNull String ruleName, final Promise promise) {
    COSLogger.removeSensitiveRule(ruleName);
    promise.resolve(null);
  }

  @ReactMethod
  public void cancelAll(@NonNull String serviceKey, final Promise promise) {
    CosXmlService service = getCosXmlService(serviceKey);
    service.cancelAll();
    promise.resolve(null);
  }

  private CosXmlService getCosXmlService(String serviceKey) {
    if (cosServices.containsKey(serviceKey)) {
      return cosServices.get(serviceKey);
    } else {
      String key = DEFAULT_KEY.equals(serviceKey) ? "default" : serviceKey;
      throw new IllegalArgumentException(key + " CosService unregistered, Please register first");
    }
  }

  private CosXmlService buildCosXmlService(Context context, @NonNull ReadableMap config) {
    CosXmlServiceConfig.Builder serviceConfigBuilder = new CosXmlServiceConfig.Builder();
    if (config.hasKey("region")) {
      serviceConfigBuilder.setRegion(config.getString("region"));
    }
    if (config.hasKey("connectionTimeout")) {
      serviceConfigBuilder.setConnectionTimeout(config.getInt("connectionTimeout"));
    }
    if (config.hasKey("socketTimeout")) {
      serviceConfigBuilder.setSocketTimeout(config.getInt("socketTimeout"));
    }
    if (config.hasKey("isHttps")) {
      serviceConfigBuilder.isHttps(config.getBoolean("isHttps"));
    }
    if (config.hasKey("domainSwitch")) {
      serviceConfigBuilder.setDomainSwitch(config.getBoolean("domainSwitch"));
    }
    if (config.hasKey("host")) {
      serviceConfigBuilder.setHost(config.getString("host"));
    }
    if (config.hasKey("hostFormat")) {
      serviceConfigBuilder.setHostFormat(config.getString("hostFormat"));
    }
    if (config.hasKey("port")) {
      serviceConfigBuilder.setPort(config.getInt("port"));
    }
    if (config.hasKey("isDebuggable")) {
      serviceConfigBuilder.setDebuggable(config.getBoolean("isDebuggable"));
    }
    if (config.hasKey("signInUrl")) {
      serviceConfigBuilder.setSignInUrl(config.getBoolean("signInUrl"));
    }
    if (config.hasKey("dnsCache")) {
      serviceConfigBuilder.dnsCache(config.getBoolean("dnsCache"));
    }
    if (config.hasKey("accelerate")) {
      serviceConfigBuilder.setAccelerate(config.getBoolean("accelerate"));
    }
    if (config.hasKey("userAgent") && !TextUtils.isEmpty(config.getString("userAgent"))) {
      serviceConfigBuilder.setUserAgentExtended(config.getString("userAgent"));
    } else {
      serviceConfigBuilder.setUserAgentExtended("ReactNativePlugin");
    }

    CosXmlService cosXmlService;
    if (qCloudCredentialProvider == null) {
      cosXmlService = new CosXmlService(context, serviceConfigBuilder.builder());
    } else {
      cosXmlService = new CosXmlService(context, serviceConfigBuilder.builder(), qCloudCredentialProvider);
    }
    if(dnsMap != null) {
      try {
        for (String domain: dnsMap.keySet()) {
          if(dnsMap.get(domain) != null && dnsMap.get(domain).length > 0){
            cosXmlService.addCustomerDNS(domain, dnsMap.get(domain));
          }
        }
      } catch (CosXmlClientException e) {
        e.printStackTrace();
      }
    }
    if(initDnsFetch){
      cosXmlService.addCustomerDNSFetch(domain -> {
        // 发送获取ips的通知
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
          .emit(Constants.COS_EMITTER_DNS_FETCH, domain);
        // 加锁等待
        synchronized (fetchDnsWaitingLock) {
          try {
            fetchDnsWaitingLock.wait(15000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        if(fetchMapCache.containsKey(domain)){
          return fetchMapCache.get(domain);
        } else {
          return null;
        }
      });
    }
    return cosXmlService;
  }

  private TransferManager getTransferManager(String transferKey) {
    if (transferManagers.containsKey(transferKey)) {
      return transferManagers.get(transferKey);
    } else {
      String key = DEFAULT_KEY.equals(transferKey) ? "default" : transferKey;
      throw new IllegalArgumentException(key + " TransferManager unregistered, Please register first");
    }
  }

  private TransferManager buildTransferManager(Context context, @NonNull ReadableMap config, @Nullable ReadableMap transferConfig) {
    TransferConfig.Builder builder = new TransferConfig.Builder();
    if (transferConfig != null) {
      if (transferConfig.hasKey("forceSimpleUpload")) {
        builder.setForceSimpleUpload(transferConfig.getBoolean("forceSimpleUpload"));
      }
      if (transferConfig.hasKey("enableVerification")) {
        builder.setVerifyCRC64(transferConfig.getBoolean("enableVerification"));
      }
      if (transferConfig.hasKey("divisionForUpload")) {
        builder.setDivisionForUpload(transferConfig.getInt("divisionForUpload"));
      }
      if (transferConfig.hasKey("sliceSizeForUpload")) {
        builder.setSliceSizeForUpload(transferConfig.getInt("sliceSizeForUpload"));
      }
    }
    CosXmlService cosXmlService = buildCosXmlService(context, config);
    return new TransferManager(cosXmlService, builder.build());
  }

  @ReactMethod
  public void upload(
    @NonNull String transferKey,
    @NonNull String bucket,
    @NonNull String cosPath,
    @NonNull String fileUri,
    @Nullable String uploadId,
    @Nullable String resultCallbackKey,
    @Nullable String stateCallbackKey,
    @Nullable String progressCallbackKey,
    @Nullable String InitMultipleUploadCallbackKey,
    @Nullable String stroageClass,
    @Nullable String trafficLimit,
    @Nullable String region,
    @Nullable ReadableMap credentials,
    final Promise promise
  ) {
    TransferManager transferManager = getTransferManager(transferKey);
    PutObjectRequest request = new PutObjectRequest(bucket, cosPath, Uri.parse(fileUri));
    if (region != null) {
      request.setRegion(region);
    }
    if (stroageClass != null) {
      request.setStroageClass(COSStorageClass.fromString(stroageClass));
    }
    if (trafficLimit != null) {
      request.setTrafficLimit(Long.parseLong(trafficLimit));
    }
    setRequestCredential(credentials, request);
    COSXMLUploadTask task = transferManager.upload(request, uploadId);
    setTaskListener(task, transferKey, resultCallbackKey, stateCallbackKey, progressCallbackKey, InitMultipleUploadCallbackKey);
    String taskKey = String.valueOf(task.hashCode());
    taskMap.put(taskKey, task);
    promise.resolve(taskKey);
  }

  @ReactMethod
  public void download(
    @NonNull String transferKey,
    @NonNull String bucket,
    @NonNull String cosPath,
    @NonNull String savePath,
    @Nullable String resultCallbackKey,
    @Nullable String stateCallbackKey,
    @Nullable String progressCallbackKey,
    @Nullable String versionId,
    @Nullable String trafficLimit,
    @Nullable String region,
    @Nullable ReadableMap credentials,
    final Promise promise
  ) {
    TransferManager transferManager = getTransferManager(transferKey);
    int separator = savePath.lastIndexOf("/");
    GetObjectRequest request = new GetObjectRequest(bucket, cosPath,
      savePath.substring(0, separator + 1),
      savePath.substring(separator + 1));
    if (region != null) {
      request.setRegion(region);
    }
    if (versionId != null) {
      request.setVersionId(versionId);
    }
    if (trafficLimit != null) {
      request.setTrafficLimit(Long.parseLong(trafficLimit));
    }
    setRequestCredential(credentials, request);
    COSXMLDownloadTask task = transferManager.download(reactContext, request);
    setTaskListener(task, transferKey, resultCallbackKey, stateCallbackKey, progressCallbackKey, null);
    String taskKey = String.valueOf(task.hashCode());
    taskMap.put(taskKey, task);
    promise.resolve(taskKey);
  }

  @ReactMethod
  public void pause(@NonNull String transferKey, @NonNull String taskId, final Promise promise) {
    COSXMLTask task = taskMap.get(taskId);
    if (task != null) {
      task.pause();
      promise.resolve(null);
    } else {
      promise.reject(new IllegalArgumentException());
    }
  }

  @ReactMethod
  public void resume(@NonNull String transferKey, @NonNull String taskId, final Promise promise) {
    COSXMLTask task = taskMap.get(taskId);
    if (task != null) {
      task.resume();
      promise.resolve(null);
    } else {
      promise.reject(new IllegalArgumentException());    }
  }

  @ReactMethod
  public void cancel(@NonNull String transferKey, @NonNull String taskId, final Promise promise) {
    COSXMLTask task = taskMap.get(taskId);
    if (task != null) {
      task.cancel();
      promise.resolve(null);
    } else {
      promise.reject(new IllegalArgumentException());
    }
  }

  private void setTaskListener(
    @NonNull COSXMLTask task,
    @NonNull String transferKey,
    @Nullable String resultCallbackKey,
    @Nullable String stateCallbackKey,
    @Nullable String progressCallbackKey,
    @Nullable String initMultipleUploadCallbackKey) {
    task.setCosXmlResultListener(new CosXmlResultListener() {
      @Override
      public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
        if (!TextUtils.isEmpty(resultCallbackKey)) {
          runMainThread(() -> {
            WritableMap params = Arguments.createMap();
            params.putString("transferKey", transferKey);
            params.putString("callbackKey", resultCallbackKey);
            WritableMap header = simplifyHeader(cosXmlResult.headers);

            if (task instanceof COSXMLUploadTask) {
              WritableMap uploadResultMap = Arguments.createMap();
              COSXMLUploadTask.COSXMLUploadTaskResult uploadResult =
                (COSXMLUploadTask.COSXMLUploadTaskResult) cosXmlResult;
              uploadResultMap.putString("accessUrl", cosXmlResult.accessUrl);
              uploadResultMap.putString("eTag", uploadResult.eTag);
              if(header.hasKey("x-cos-hash-crc64ecma")){
                uploadResultMap.putString("crc64ecma", header.getString("x-cos-hash-crc64ecma"));
              }
                params.putMap("headers", uploadResultMap);
              } else {
                params.putMap("headers", header);
              }
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
              .emit(Constants.COS_EMITTER_RESULT_SUCCESS_CALLBACK, params);
            }
          );
        }
        String taskKey = String.valueOf(task.hashCode());
        taskMap.remove(taskKey);
      }

      @Override
      public void onFail(CosXmlRequest cosXmlRequest, @Nullable CosXmlClientException e, @Nullable CosXmlServiceException e1) {
        if (!TextUtils.isEmpty(resultCallbackKey)) {
          runMainThread(() -> {
              WritableMap params = Arguments.createMap();
              params.putString("transferKey", transferKey);
              params.putString("callbackKey", resultCallbackKey);
              params.putMap("clientException", toCosXmlClientExceptionWritableMap(e));
              params.putMap("serviceException", toCosXmlServiceExceptionWritableMap(e1));
              reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(Constants.COS_EMITTER_RESULT_FAIL_CALLBACK, params);
            }
          );
        }
        String taskKey = String.valueOf(task.hashCode());
        taskMap.remove(taskKey);
      }
    });
    task.setCosXmlProgressListener((l, l1) -> {
      if (!TextUtils.isEmpty(progressCallbackKey)) {
        runMainThread(() -> {
          WritableMap params = Arguments.createMap();
          params.putString("transferKey", transferKey);
          params.putString("callbackKey", progressCallbackKey);
          params.putString("complete", String.valueOf(l));
          params.putString("target", String.valueOf(l1));
          reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(Constants.COS_EMITTER_PROGRESS_CALLBACK, params);
        });
      }
    });
    task.setTransferStateListener(transferState -> {
      if (!TextUtils.isEmpty(stateCallbackKey)) {
        runMainThread(() -> {
          WritableMap params = Arguments.createMap();
          params.putString("transferKey", transferKey);
          params.putString("callbackKey", stateCallbackKey);
          params.putString("state", transferState.toString());
          reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(Constants.COS_EMITTER_STATE_CALLBACK, params);
        });
      }
    });
    //上传注册 分块上传初始化完成的回调
    if (task instanceof COSXMLUploadTask) {
      task.setInitMultipleUploadListener(new InitMultipleUploadListener() {
        @Override
        public void onSuccess(InitiateMultipartUpload initiateMultipartUpload) {
          if (!TextUtils.isEmpty(initMultipleUploadCallbackKey)) {
            runMainThread(() -> {
              WritableMap params = Arguments.createMap();
              params.putString("transferKey", transferKey);
              params.putString("callbackKey", initMultipleUploadCallbackKey);
              params.putString("bucket", initiateMultipartUpload.bucket);
              params.putString("key", initiateMultipartUpload.key);
              params.putString("uploadId", initiateMultipartUpload.uploadId);
              reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(Constants.COS_EMITTER_INIT_MULTIPLE_UPLOAD_CALLBACK, params);
            });
          }
        }
      });
    }
  }

  private WritableMap toCosXmlClientExceptionWritableMap(CosXmlClientException e) {
    if (e != null) {
      WritableMap writableMap = Arguments.createMap();
      writableMap.putInt("errorCode", e.errorCode);
      writableMap.putString("message", e.getMessage());
      return writableMap;
    } else {
      return null;
    }
  }

  private WritableMap toCosXmlServiceExceptionWritableMap(CosXmlServiceException e) {
    if (e != null) {
      WritableMap writableMap = Arguments.createMap();
      writableMap.putInt("statusCode", e.getStatusCode());
      writableMap.putString("httpMsg", e.getHttpMessage());
      writableMap.putString("requestId", e.getRequestId());
      writableMap.putString("serviceName", e.getServiceName());
      writableMap.putString("errorCode", e.getErrorCode());
      writableMap.putString("errorMessage", e.getErrorMessage());
      return writableMap;
    } else {
      return null;
    }
  }

  // 转换工具方法
  private byte[] convertReadableArrayToByteArray(ReadableArray array) {
    if (array == null || array.size() == 0) {
        return new byte[0];
    }

    byte[] bytes = new byte[array.size()];
    for (int i = 0; i < array.size(); i++) {
        // Uint8Array 的数值范围是 0-255，转换为 byte 的 -128~127
        int value = array.getInt(i);
        bytes[i] = (byte) (value & 0xFF);
    }
    return bytes;
  }

  private WritableMap simplifyHeader(Map<String, List<String>> headers) {
    if (headers == null) return null;

    WritableMap writableMap = Arguments.createMap();
    for (String key : headers.keySet()) {
      List<String> values = headers.get(key);
      if (values != null && !values.isEmpty()) {
        writableMap.putString(key, values.get(0));
      }
    }
    return writableMap;
  }

  private void runMainThread(Runnable runnable) {
    Handler mainHandler = new Handler(Looper.getMainLooper());
    mainHandler.post(runnable);
  }

  // Required for rn built in EventEmitter Calls.
  @ReactMethod
  public void addListener(String eventName) {
  }
  @ReactMethod
  public void removeListeners(Integer count) {
  }

  private void setRequestCredential(@Nullable ReadableMap sessionCredentials, @NonNull CosXmlRequest request){
    if (sessionCredentials != null){
      SessionQCloudCredentials credentials;
      if (!sessionCredentials.hasKey("startTime")) {
        credentials = new SessionQCloudCredentials(
          sessionCredentials.getString("tmpSecretId"),
          sessionCredentials.getString("tmpSecretKey"),
          sessionCredentials.getString("sessionToken"),
          (long) sessionCredentials.getDouble("expiredTime"));
      } else {
        credentials = new SessionQCloudCredentials(
          sessionCredentials.getString("tmpSecretId"),
          sessionCredentials.getString("tmpSecretKey"),
          sessionCredentials.getString("sessionToken"),
          (long) sessionCredentials.getDouble("startTime"),
          (long) sessionCredentials.getDouble("expiredTime"));
      }
      Log.d("setRequestCredential", "setCredential: " + credentials.toString());
      request.setCredential(credentials);
    }
  }
}
