/*
 *  * Copyright © Wynntils - 2019.
 */

package com.wynntils.modules.richpresence.profiles;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.wynntils.ModCore;
import com.wynntils.Reference;
import com.wynntils.core.framework.instances.PlayerInfo;
import com.wynntils.core.utils.helpers.MD5Verification;
import com.wynntils.modules.richpresence.discordgamesdk.*;
import com.wynntils.modules.richpresence.events.RPCJoinHandler;
import com.wynntils.webapi.WebManager;
import com.wynntils.webapi.WebRequestHandler;
import com.wynntils.webapi.downloader.DownloaderManager;
import com.wynntils.webapi.downloader.enums.DownloadAction;

import java.io.File;
import java.time.OffsetDateTime;

public class RichProfile {

    IDiscordCore.ByReference discordCore = null;
    IDiscordActivityManager activityManager = null;
    Thread shutdown = new Thread(this::disconnectRichPresence);

    SecretContainer joinSecret = null;

    DiscordActivity lastStructure = null;

    boolean disabled = false;
    boolean ready = false;
    DiscordActivity activityToUseWhenReady = null;

    boolean updatedDiscordUser = false;

    long applicationID = 0;

    public RichProfile(long id) {
        File natives = new File(Reference.MOD_STORAGE_ROOT, "natives");
        File file = new File(natives, System.mapLibraryName("discord_game_sdk"));
        WebRequestHandler handler = new WebRequestHandler();
        handler.addRequest(new WebRequestHandler.Request(WebManager.getApiUrls().get("RichPresence") + "versioning.php", "richpresence_versioning")
            .cacheTo(new File(natives, "richpresence_versioning.txt"))
            .handleWebReader(reader -> {
                String md5 = reader.get(Platform.RESOURCE_PREFIX);
                if (!file.exists() || !new MD5Verification(file).equals(md5)) {
                    DownloaderManager.queueDownload("Discord Game SDK", WebManager.getApiUrls().get("RichPresence") + Platform.RESOURCE_PREFIX + "/" + System.mapLibraryName("discord_game_sdk"), new File(Reference.MOD_STORAGE_ROOT, "natives"), DownloadAction.SAVE, (success) -> {
                        if (success) {
                            ModCore.mc().addScheduledTask(() -> {
                                setup(id);
                            });
                        } else {
                            setDisabled();
                        }
                    });
                } else {
                    setup(id);
                }
                return true;
        }));
        handler.dispatchAsync();
    }

    private void setup(long id) {
        try {
            applicationID = id;
            DiscordGameSDKLibrary gameSDK = DiscordGameSDKLibrary.INSTANCE;

            IDiscordUserEvents.ByReference userEvents = new IDiscordUserEvents.ByReference();
            userEvents.on_current_user_update = eventData -> {
                DiscordUser user = new DiscordUser();
                discordCore.get_user_manager.apply(discordCore).get_current_user.apply(discordCore.get_user_manager.apply(discordCore), user);
                if (WebManager.getAccount() != null) WebManager.getAccount().updateDiscord(Long.toString(user.id), bytesToString(user.username) + "#" + bytesToString(user.discriminator));
            };
            IDiscordActivityEvents.ByReference activityEvents = new IDiscordActivityEvents.ByReference();
            activityEvents.on_activity_join = new RPCJoinHandler();

            DiscordCreateParams createParams = new DiscordCreateParams();
            createParams.application_version = DiscordGameSDKLibrary.DISCORD_APPLICATION_MANAGER_VERSION;
            createParams.user_version = DiscordGameSDKLibrary.DISCORD_USER_MANAGER_VERSION;
            createParams.image_version = DiscordGameSDKLibrary.DISCORD_IMAGE_MANAGER_VERSION;
            createParams.activity_version = DiscordGameSDKLibrary.DISCORD_ACTIVITY_MANAGER_VERSION;
            createParams.relationship_version = DiscordGameSDKLibrary.DISCORD_RELATIONSHIP_MANAGER_VERSION;
            createParams.lobby_version = DiscordGameSDKLibrary.DISCORD_LOBBY_MANAGER_VERSION;
            createParams.network_version = DiscordGameSDKLibrary.DISCORD_NETWORK_MANAGER_VERSION;
            createParams.overlay_version = DiscordGameSDKLibrary.DISCORD_OVERLAY_MANAGER_VERSION;
            createParams.storage_version = DiscordGameSDKLibrary.DISCORD_STORAGE_MANAGER_VERSION;
            createParams.store_version = DiscordGameSDKLibrary.DISCORD_STORE_MANAGER_VERSION;
            createParams.voice_version = DiscordGameSDKLibrary.DISCORD_VOICE_MANAGER_VERSION;
            createParams.achievement_version = DiscordGameSDKLibrary.DISCORD_ACHIEVEMENT_MANAGER_VERSION;
            createParams.client_id = id;
            createParams.user_events = userEvents;
            createParams.activity_events = activityEvents;
            createParams.flags = DiscordGameSDKLibrary.EDiscordCreateFlags.DiscordCreateFlags_NoRequireDiscord;

            discordCore = new IDiscordCore.ByReference();
            IDiscordCore.ByReference[] array = new IDiscordCore.ByReference[] { discordCore };
            gameSDK.DiscordCreate(DiscordGameSDKLibrary.DISCORD_VERSION, createParams, array);
            discordCore = array[0];
            // get_user_manager is needed so the current user update is fired
            discordCore.get_user_manager.apply(discordCore);
            activityManager = discordCore.get_activity_manager.apply(discordCore);

            Runtime.getRuntime().addShutdownHook(shutdown);
            ready = true;
            if (activityToUseWhenReady != null) {
                activityManager.update_activity.apply(activityManager, activityToUseWhenReady, null, null);
                activityToUseWhenReady = null;
            }
        } catch (UnsatisfiedLinkError e) {
            Reference.LOGGER.error("Unable to open Discord Game SDK Library.");
            e.printStackTrace();
            setDisabled();
        }
    }

    private void setDisabled() {
        activityToUseWhenReady = null;
        disabled = true;
    }

    /**
     * Cleans user current RichPresence
     */
    public void stopRichPresence() {
        if (disabled) return;
        if (ready) {
            activityManager.clear_activity.apply(activityManager, null, null);
        } else {
            activityToUseWhenReady = null;
        }
    }

    /**
     * update user RichPresence
     *
     * @param state
     *        RichPresence state string
     * @param details
     *        RichPresence details string
     * @param largText
     *        RichPresence large Text
     * @param date
     *        RichPresence Date
     */
    public void updateRichPresence(String state, String details, String largText, OffsetDateTime date) {
        if (disabled) return;
        DiscordActivity richPresence = new DiscordActivity();
        richPresence.state = toBytes(state, 128);
        richPresence.details = toBytes(details, 128);
        richPresence.application_id = applicationID;
        DiscordActivityAssets richPresenceAssets = new DiscordActivityAssets();
        richPresenceAssets.large_text = toBytes(largText, 128);
        richPresenceAssets.large_image = toBytes("wynn", 128);
        DiscordActivityTimestamps richPresenceTimestamps = new DiscordActivityTimestamps();
        richPresenceTimestamps.start = date.toInstant().getEpochSecond();
        DiscordActivitySecrets richPresenceSecrets = new DiscordActivitySecrets();
        DiscordActivityParty richPresenceParty = new DiscordActivityParty();

        if (joinSecret != null) {
            richPresenceSecrets.join = toBytes(joinSecret.toString(), 128);
            richPresenceParty.id = toBytes(joinSecret.id, 128);
            DiscordPartySize partySize = new DiscordPartySize();
            partySize.current_size = PlayerInfo.getPlayerInfo().getPlayerParty().getPartyMembers().size();
            partySize.max_size = 15;
            richPresenceParty.size = partySize;
        }
        richPresence.assets = richPresenceAssets;
        richPresence.timestamps = richPresenceTimestamps;
        richPresence.secrets = richPresenceSecrets;
        richPresence.party = richPresenceParty;

        lastStructure = richPresence;

        if (ready) {
            activityManager.update_activity.apply(activityManager, richPresence, null, null);
        } else {
            activityToUseWhenReady = richPresence;
        }
    }

    /**
     * update user RichPresence
     *
     * @param state
     *        RichPresence state string
     * @param details
     *        RichPresence details string
     * @param largText
     *        RichPresence large Text
     * @param largeImg
     *        RichPresence large image key
     * @param date
     *        RichPresence Date
     */
    public void updateRichPresence(String state, String details, String largeImg, String largText, OffsetDateTime date) {
        if (disabled) return;
        DiscordActivity richPresence = new DiscordActivity();
        richPresence.state = toBytes(state, 128);
        richPresence.details = toBytes(details, 128);
        richPresence.application_id = applicationID;
        DiscordActivityAssets richPresenceAssets = new DiscordActivityAssets();
        richPresenceAssets.large_image = toBytes(largeImg, 128);
        richPresenceAssets.large_text = toBytes(largText, 128);
        DiscordActivityTimestamps richPresenceTimestamps = new DiscordActivityTimestamps();
        richPresenceTimestamps.start = date.toInstant().getEpochSecond();
        DiscordActivitySecrets richPresenceSecrets = new DiscordActivitySecrets();
        DiscordActivityParty richPresenceParty = new DiscordActivityParty();

        if (joinSecret != null) {
            richPresenceSecrets.join = toBytes(joinSecret.toString(), 128);
            richPresenceParty.id = toBytes(joinSecret.id, 128);
            DiscordPartySize partySize = new DiscordPartySize();
            partySize.current_size = PlayerInfo.getPlayerInfo().getPlayerParty().getPartyMembers().size();
            partySize.max_size = 15;
            richPresenceParty.size = partySize;
        }
        richPresence.assets = richPresenceAssets;
        richPresence.timestamps = richPresenceTimestamps;
        richPresence.secrets = richPresenceSecrets;
        richPresence.party = richPresenceParty;

        lastStructure = richPresence;

        if (ready) {
            activityManager.update_activity.apply(activityManager, richPresence, null, null);
        } else {
            activityToUseWhenReady = richPresence;
        }
    }

    /**
     * update user RichPresence
     *
     * @param state
     *        RichPresence state string
     * @param details
     *        RichPresence details string
     * @param largText
     *        RichPresence large Text
     * @param date
     *        RichPresence End Date
     */
    public void updateRichPresenceEndDate(String state, String details, String largText, OffsetDateTime date) {
        if (disabled) return;
        DiscordActivity richPresence = new DiscordActivity();
        richPresence.state = toBytes(state, 128);
        richPresence.details = toBytes(details, 128);
        richPresence.application_id = applicationID;
        DiscordActivityAssets richPresenceAssets = new DiscordActivityAssets();
        richPresenceAssets.large_image = toBytes("wynn", 128);
        richPresenceAssets.large_text = toBytes(largText, 128);
        DiscordActivityTimestamps richPresenceTimestamps = new DiscordActivityTimestamps();
        richPresenceTimestamps.end = date.toInstant().getEpochSecond();
        DiscordActivitySecrets richPresenceSecrets = new DiscordActivitySecrets();
        DiscordActivityParty richPresenceParty = new DiscordActivityParty();

        if (joinSecret != null) {
            richPresenceSecrets.join = toBytes(joinSecret.toString(), 128);
            richPresenceParty.id = toBytes(joinSecret.id, 128);
            DiscordPartySize partySize = new DiscordPartySize();
            partySize.current_size = PlayerInfo.getPlayerInfo().getPlayerParty().getPartyMembers().size();
            partySize.max_size = 15;
            richPresenceParty.size = partySize;
        }
        richPresence.assets = richPresenceAssets;
        richPresence.timestamps = richPresenceTimestamps;
        richPresence.secrets = richPresenceSecrets;
        richPresence.party = richPresenceParty;

        lastStructure = richPresence;

        if (ready) {
            activityManager.update_activity.apply(activityManager, richPresence, null, null);
        } else {
            activityToUseWhenReady = richPresence;
        }
    }

    /**
     * update user RichPresence
     *
     * @param state
     *        RichPresence state string
     * @param details
     *        RichPresence details string
     * @param largText
     *        RichPresence large Text
     * @param largeImg
     *        RichPresence large image key
     * @param date
     *        RichPresence End Date
     */
    public void updateRichPresenceEndDate(String state, String details, String largeImg, String largText, OffsetDateTime date) {
        if (disabled) return;
        DiscordActivity richPresence = new DiscordActivity();
        richPresence.state = toBytes(state, 128);
        richPresence.details = toBytes(details, 128);
        richPresence.application_id = applicationID;
        DiscordActivityAssets richPresenceAssets = new DiscordActivityAssets();
        richPresenceAssets.large_image = toBytes(largeImg, 128);
        richPresenceAssets.large_text = toBytes(largText, 128);
        DiscordActivityTimestamps richPresenceTimestamps = new DiscordActivityTimestamps();
        richPresenceTimestamps.end = date.toInstant().getEpochSecond();
        DiscordActivitySecrets richPresenceSecrets = new DiscordActivitySecrets();
        DiscordActivityParty richPresenceParty = new DiscordActivityParty();

        if (joinSecret != null) {
            richPresenceSecrets.join = toBytes(joinSecret.toString(), 128);
            richPresenceParty.id = toBytes(joinSecret.id, 128);
            DiscordPartySize partySize = new DiscordPartySize();
            partySize.current_size = PlayerInfo.getPlayerInfo().getPlayerParty().getPartyMembers().size();
            partySize.max_size = 15;
            richPresenceParty.size = partySize;
        }
        richPresence.assets = richPresenceAssets;
        richPresence.timestamps = richPresenceTimestamps;
        richPresence.secrets = richPresenceSecrets;
        richPresence.party = richPresenceParty;

        lastStructure = richPresence;

        if (ready) {
            activityManager.update_activity.apply(activityManager, richPresence, null, null);
        } else {
            activityToUseWhenReady = richPresence;
        }
    }

    /**
     * Runs all the callbacks from the RPC
     */
    public void runCallbacks() {
        if (disabled || !ready) return;

        discordCore.run_callbacks.apply(discordCore);
    }

    /**
     * Updates the Join Secret
     *
     * @param joinSecret the join secret
     */
    public void setJoinSecret(SecretContainer joinSecret) {
        if (disabled) return;
        this.joinSecret = joinSecret;

        if (lastStructure != null) {
            if (joinSecret != null) {
                lastStructure.secrets.join = toBytes(joinSecret.toString(), 128);
                lastStructure.party.id = toBytes(joinSecret.id, 128);
                lastStructure.party.size.current_size = PlayerInfo.getPlayerInfo().getPlayerParty().getPartyMembers().size();
                lastStructure.party.size.max_size = 15;
            } else {
                lastStructure.secrets.join = toBytes(null, 128);
                lastStructure.party.id = toBytes(null, 128);
                lastStructure.party.size.current_size = 0;
                lastStructure.party.size.max_size = 0;
            }
            if (ready) {
                activityManager.update_activity.apply(activityManager, lastStructure, null, null);
            } else {
                activityToUseWhenReady = lastStructure;
            }
        }
    }

    public boolean validSecrent(String secret) {
        if (disabled) return false;
        return joinSecret != null && joinSecret.getRandomHash().equals(secret);
    }

    /**
     * Shutdown the RPC
     */
    public void disconnectRichPresence() {
        if (disabled) return;
        discordCore.destroy.apply(discordCore);
    }

    /**
     * Gets the join secret container
     *
     * @return the join secret container
     */
    public SecretContainer getJoinSecret() {
        if (disabled) return null;
        return joinSecret;
    }

    public boolean isDisabled() {
        return disabled;
    }

    private byte[] toBytes(String string, int size) {
        if (string == null) {
            return new byte[size];
        }
        byte[] array = Native.toByteArray(string);
        byte[] newArray = new byte[size];
        System.arraycopy(array, 0, newArray, 0, array.length);
        return newArray;
    }

    private String bytesToString(byte[] bytes) {
        return Native.toString(bytes);
    }

}
