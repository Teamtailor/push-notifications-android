package com.pusher.pushnotifications

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Handler
import android.os.Looper
import com.google.firebase.iid.FirebaseInstanceId
import com.pusher.pushnotifications.api.OperationCallback
import com.pusher.pushnotifications.api.PushNotificationsAPI
import com.pusher.pushnotifications.fcm.FCMInstanceIDService
import com.pusher.pushnotifications.logging.Logger
import com.pusher.pushnotifications.validation.Validations

/**
 * Interacts with the Pusher service to subscribe and unsubscribe from interests.
 *
 * @param context the application context
 * @param instanceId the id of the instance
 */
class PushNotificationsInstance(
  context: Context,
  instanceId: String) {
  init {
    Validations.validateApplicationIcon(context)
  }

  private val preferencesDeviceIdKey = "deviceId"
  private val preferencesFcmTokenKey = "fcmToken"
  private val preferencesInterestsSetKey = "interests"

  private val localPreferences = context.getSharedPreferences(this::class.java.name, MODE_PRIVATE)
  private val log = Logger.get(this::class)

  private val api = PushNotificationsAPI(instanceId)

  /**
   * Starts the PushNotification client and synchronizes the FCM device token with
   * the Pusher services.
   */
  fun start(): PushNotificationsInstance {
    localPreferences.getString(preferencesDeviceIdKey, null)?.let {
      api.deviceId = it
    }

    localPreferences.getString(preferencesFcmTokenKey, null)?.let {
      api.fcmToken = it
    }

    val handleFcmToken = { fcmToken: String ->
      api.registerOrRefreshFCM(fcmToken, {
        object : OperationCallback {
          override fun onSuccess() {
            localPreferences.edit()
              .putString(preferencesDeviceIdKey, api.deviceId)
              .putString(preferencesFcmTokenKey, fcmToken)
              .apply()
            log.w("Successfully started PushNotifications")
          }

          override fun onFailure(t: Throwable) {
            log.w("Failed to start PushNotifications", t)
          }
        }
      }())
    }

    FCMInstanceIDService.onRefreshToken = handleFcmToken
    FirebaseInstanceId.getInstance().token?.let(handleFcmToken)
    return this
  }

  /**
   * Subscribes the device to an interest. For example:
   * <pre>{@code pushNotifications.subscribe("hello");}</pre>
   * @param interest the name of the interest
   */
  fun subscribe(interest: String) {
    synchronized(localPreferences) {
      val interestsSet = localPreferences.getStringSet(preferencesInterestsSetKey, mutableSetOf<String>())
      if (!interestsSet.add(interest)) {
        return // not a new interest
      }
      localPreferences.edit().putStringSet(preferencesInterestsSetKey, interestsSet).apply()
    }
    api.subscribe(interest, OperationCallback.noop)
  }

  /**
   * Unsubscribes the device from an interest. For example:
   * <pre>{@code pushNotifications.unsubscribe("hello");}</pre>
   * @param interest the name of the interest
   */
  fun unsubscribe(interest: String) {
    synchronized(localPreferences) {
      val interestsSet = localPreferences.getStringSet(preferencesInterestsSetKey, mutableSetOf<String>())
      if (!interestsSet.remove(interest)) {
        return // interest wasn't present
      }
      localPreferences.edit().putStringSet(preferencesInterestsSetKey, interestsSet).apply()
    }
    api.unsubscribe(interest, OperationCallback.noop)
  }

  /**
   * Unsubscribes the device from all the interests. For example:
   * <pre>{@code pushNotifications.unsubscribeAll();}</pre>
   */
  fun unsubscribeAll() {
    setSubscriptions(emptySet())
  }

  /**
   * Sets the subscriptions state for the device. Any interests not in the set will be
   * unsubscribed from, so this will replace the interest set by the one provided.
   * <br>
   * For example:
   * <pre>{@code pushNotifications.setSubscriptions(Arrays.asList("hello", "donuts").toSet());}</pre>
   * @param interests the new set of interests
   */
  fun setSubscriptions(interests: Set<String>) {
    synchronized(localPreferences) {
      val localInterestsSet = localPreferences.getStringSet(preferencesInterestsSetKey, mutableSetOf<String>())
      if (localInterestsSet.containsAll(interests) && interests.containsAll(localInterestsSet)) {
        return // they are the same
      }
      localPreferences.edit().putStringSet(preferencesInterestsSetKey, interests).apply()
    }
    api.setSubscriptions(interests, OperationCallback.noop)
  }

  /**
   * @return the set of subscriptions that the device is currently subscribed to
   */
  fun getSubscriptions(): Set<String> {
    synchronized(localPreferences) {
      return localPreferences.getStringSet(preferencesInterestsSetKey, mutableSetOf<String>())
    }
  }
}