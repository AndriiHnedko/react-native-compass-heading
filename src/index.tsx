import {
  NativeModules,
  NativeEventEmitter,
  Platform,
  type NativeModule,
} from 'react-native';

export interface HeadingData {
  heading: number;
  accuracy: number;
}

export interface CompassHeadingModule extends NativeModule {
  start(
    updateRate: number,
    callback: (data: HeadingData) => void
  ): Promise<void>;

  stop(): Promise<void>;

  hasCompass(): Promise<boolean | null>;
}

const LINKING_ERROR =
  `The package 'react-native-compass-heading' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({
    ios: "- You have run 'pod install'\n",
    default: '',
  }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const CompassHeading: CompassHeadingModule = NativeModules.CompassHeading
  ? NativeModules.CompassHeading
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

let listener: { remove: () => any } | null = null;

let _start = CompassHeading.start;

CompassHeading.start = async (
  update_rate: number,
  callback: (data: HeadingData) => void
) => {
  if (listener) {
    await CompassHeading.stop(); // Clean up previous listener
  }

  const compassEventEmitter = new NativeEventEmitter(CompassHeading);
  listener = compassEventEmitter.addListener(
    'HeadingUpdated',
    (data: HeadingData) => {
      callback(data);
    }
  );

  //@ts-ignore
  const result = await _start(update_rate === null ? 0 : update_rate);
  return result;
};

let _stop = CompassHeading.stop;
CompassHeading.stop = async () => {
  if (listener) {
    listener.remove();
    listener = null;
  }
  await _stop();
};

let _hasCompass = CompassHeading.hasCompass;

CompassHeading.hasCompass = async () => {
  if (Platform.OS === 'android') return _hasCompass();
  if (Platform.OS === 'ios') return true;
  return null;
};

export default CompassHeading;
