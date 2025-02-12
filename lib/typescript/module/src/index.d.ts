import { type NativeModule } from 'react-native';
export interface HeadingData {
    heading: number;
    accuracy: number;
}
export interface CompassHeadingModule extends NativeModule {
    start(updateRate: number, callback: (data: HeadingData) => void): Promise<void>;
    stop(): Promise<void>;
    hasCompass(): Promise<boolean>;
}
declare const CompassHeading: CompassHeadingModule;
export default CompassHeading;
//# sourceMappingURL=index.d.ts.map