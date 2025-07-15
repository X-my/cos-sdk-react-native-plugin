import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { CosXmlServiceConfig, TransferConfig } from './data_model/config';
import type { SessionQCloudCredentials } from './data_model/credentials';
import type { DnsMapParameters } from './data_model/parameters';

export interface Spec extends TurboModule {
  initWithPlainSecret(secretId: string, secretKey: string): Promise<void>;
  initWithSessionCredentialCallback(): Promise<void>;
  initWithScopeLimitCredentialCallback(): Promise<void>;
  updateSessionCredential(credential: SessionQCloudCredentials, stsScopesArrayJson: string | null): void;
  initCustomerDNS(dnsMap: Array<DnsMapParameters>): Promise<void>;
  initCustomerDNSFetch(): Promise<void>;
  setDNSFetchIps(domain: string, ips: Array<string> | null): void;
  forceInvalidationCredential(): Promise<void>;
  setCloseBeacon(isCloseBeacon: boolean): Promise<void>;
  registerDefaultService(config: CosXmlServiceConfig): Promise<void>;
  registerDefaultTransferManger(config: CosXmlServiceConfig, transferConfig?: TransferConfig): Promise<void>;
  registerService(key: string, config: CosXmlServiceConfig): Promise<void>;
  registerTransferManger(key: string, config: CosXmlServiceConfig, transferConfig?: TransferConfig): Promise<void>;
  getObjectUrl(serviceKey: string, bucket: string, cosPath: string, region: string): Promise<string>;
  getPresignedUrl(serviceKey: string, bucket: string, cosPath: string, signValidTime?: string, signHost?: string, parameters?: object, region?: string, sessionCredentials?: SessionQCloudCredentials): Promise<string>;
  headObject(serviceKey: string, bucket: string, cosPath: string, region?: string, versionId?: string, sessionCredentials?: SessionQCloudCredentials): Promise<object>;
  deleteObject(serviceKey: string, bucket: string, cosPath: string, region?: string, versionId?: string, sessionCredentials?: SessionQCloudCredentials): Promise<void>;
  preBuildConnection(serviceKey: string, bucket: string): Promise<void>;
  getService(serviceKey: string, sessionCredentials?: SessionQCloudCredentials): Promise<string>;
  getBucket(serviceKey: string, bucket: string, region?: string, prefix?: string, delimiter?: string, encodingType?: string, marker?: string, maxKeys?: string, sessionCredentials?: SessionQCloudCredentials): Promise<string>;
  putBucket(serviceKey: string, bucket: string, region?: string, enableMAZ?: string, cosacl?: string, readAccount?: string, writeAccount?: string, readWriteAccount?: string, sessionCredentials?: SessionQCloudCredentials): Promise<void>;
  headBucket(serviceKey: string, bucket: string, region?: string, sessionCredentials?: SessionQCloudCredentials): Promise<object>;
  deleteBucket(serviceKey: string, bucket: string, region?: string, sessionCredentials?: SessionQCloudCredentials): Promise<void>;
  getBucketAccelerate(serviceKey: string, bucket: string, region?: string, sessionCredentials?: SessionQCloudCredentials): Promise<boolean>;
  putBucketAccelerate(serviceKey: string, bucket: string, enable: boolean, region?: string, sessionCredentials?: SessionQCloudCredentials): Promise<void>;
  getBucketVersioning(serviceKey: string, bucket: string, region?: string, sessionCredentials?: SessionQCloudCredentials): Promise<boolean>;
  putBucketVersioning(serviceKey: string, bucket: string, enable: boolean, region?: string, sessionCredentials?: SessionQCloudCredentials): Promise<void>;
  getBucketLocation(serviceKey: string, bucket: string, region?: string, sessionCredentials?: SessionQCloudCredentials): Promise<string>;
  doesBucketExist(serviceKey: string, bucket: string): Promise<boolean>;
  doesObjectExist(serviceKey: string, bucket: string, cosPath: string): Promise<boolean>;
  cancelAll(serviceKey: string): Promise<void>;
  upload(transferKey: string, bucket: string, cosPath: string, fileUri: string, uploadId: string | undefined, resultCallbackKey: string | undefined, stateCallbackKey: string | undefined, progressCallbackKey: string | undefined, initMultipleUploadCallbackKey: string | undefined, stroageClass: string | undefined, trafficLimit: string | undefined, region: string | undefined, sessionCredentials: any): Promise<string>;
  download(transferKey: string, bucket: string, cosPath: string, savePath: string, resultCallbackKey: string | undefined, stateCallbackKey: string | undefined, progressCallbackKey: string | undefined, versionId: string | undefined, trafficLimit: string | undefined, region: string | undefined, sessionCredentials: any): Promise<string>;
  pause(transferKey: string, taskId: string): Promise<void>;
  resume(transferKey: string, taskId: string): Promise<void>;
  cancel(transferKey: string, taskId: string): Promise<void>;
}

export default TurboModuleRegistry.get<Spec>('QCloudCosReactNative');