package de.pixart.messenger.ui.adapter;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import de.pixart.messenger.R;
import de.pixart.messenger.databinding.ConversationListRowBinding;
import de.pixart.messenger.entities.Conversation;
import de.pixart.messenger.entities.Message;
import de.pixart.messenger.entities.MucOptions;
import de.pixart.messenger.ui.ConversationFragment;
import de.pixart.messenger.ui.XmppActivity;
import de.pixart.messenger.ui.util.StyledAttributes;
import de.pixart.messenger.utils.EmojiWrapper;
import de.pixart.messenger.utils.IrregularUnicodeDetector;
import de.pixart.messenger.utils.UIHelper;
import de.pixart.messenger.xmpp.chatstate.ChatState;
import rocks.xmpp.addr.Jid;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

    private XmppActivity activity;
    private List<Conversation> conversations;
    private OnConversationClickListener listener;

    public ConversationAdapter(XmppActivity activity, List<Conversation> conversations) {
        this.activity = activity;
        this.conversations = conversations;
    }

    private static boolean cancelPotentialWork(Conversation conversation, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Conversation oldConversation = bitmapWorkerTask.conversation;
            if (oldConversation == null || conversation != oldConversation) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversationViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.conversation_list_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder viewHolder, int position) {
        Conversation conversation = conversations.get(position);
        if (conversation == null) {
            return;
        }
        CharSequence name = conversation.getName();
        if (name instanceof Jid) {
            viewHolder.binding.conversationName.setText(IrregularUnicodeDetector.style(activity, (Jid) name));
        } else {
            viewHolder.binding.conversationName.setText(EmojiWrapper.transform(name));
        }

        if (conversation == ConversationFragment.getConversation(activity)) {
            viewHolder.binding.frame.setBackgroundColor(StyledAttributes.getColor(activity, R.attr.color_background_tertiary));
        } else {
            viewHolder.binding.frame.setBackgroundColor(StyledAttributes.getColor(activity, R.attr.color_background_secondary));
        }

        Message message = conversation.getLatestMessage();
        final int failedCount = conversation.failedCount();
        final int unreadCount = conversation.unreadCount();
        final boolean isRead = conversation.isRead();
        final Conversation.Draft draft = isRead ? conversation.getDraft() : null;

        viewHolder.binding.indicatorReceived.setVisibility(View.GONE);
        viewHolder.binding.indicatorRead.setVisibility(View.GONE);
        viewHolder.binding.unreadCount.setVisibility(View.GONE);
        viewHolder.binding.failedCount.setVisibility(View.GONE);

        if (isRead) {
            viewHolder.binding.conversationName.setTypeface(null, Typeface.NORMAL);
        } else {
            viewHolder.binding.conversationName.setTypeface(null, Typeface.BOLD);
        }

        if (unreadCount > 0) {
            viewHolder.binding.unreadCount.setVisibility(View.VISIBLE);
            viewHolder.binding.unreadCount.setUnreadCount(unreadCount);
        } else {
            viewHolder.binding.unreadCount.setVisibility(View.GONE);
        }
        if (failedCount > 0) {
            viewHolder.binding.failedCount.setVisibility(View.VISIBLE);
            viewHolder.binding.failedCount.setFailedCount(failedCount);
        } else {
            viewHolder.binding.failedCount.setVisibility(View.GONE);
        }

        if (draft != null) {
            viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
            viewHolder.binding.conversationLastmsg.setText(EmojiWrapper.transform(draft.getMessage()));
            viewHolder.binding.senderName.setText(R.string.draft);
            viewHolder.binding.senderName.setVisibility(View.VISIBLE);
            viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.NORMAL);
            viewHolder.binding.senderName.setTypeface(null, Typeface.ITALIC);
        } else {
            final boolean fileAvailable = !message.isFileDeleted();
            final boolean showPreviewText;
            if (fileAvailable && (message.isFileOrImage() || message.treatAsDownloadable() || message.isGeoUri())) {
                final int imageResource;
                if (message.isGeoUri()) {
                    imageResource = activity.getThemeResource(R.attr.ic_attach_location, R.drawable.ic_attach_location);
                    showPreviewText = false;
                } else {
                    final String mime = message.getMimeType();
                    switch (mime == null ? "" : mime.split("/")[0]) {
                        case "image":
                            imageResource = activity.getThemeResource(R.attr.ic_attach_photo, R.drawable.ic_attach_photo);
                            showPreviewText = false;
                            break;
                        case "video":
                            imageResource = activity.getThemeResource(R.attr.ic_attach_video, R.drawable.ic_attach_video);
                            showPreviewText = false;
                            break;
                        case "audio":
                            imageResource = activity.getThemeResource(R.attr.ic_attach_record, R.drawable.ic_attach_record);
                            showPreviewText = false;
                            break;
                        default:
                            imageResource = activity.getThemeResource(R.attr.ic_attach_document, R.drawable.ic_attach_document);
                            showPreviewText = true;
                            break;
                    }
                }
                viewHolder.binding.conversationLastmsgImg.setImageResource(imageResource);
                viewHolder.binding.conversationLastmsgImg.setVisibility(View.VISIBLE);
            } else {
                viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
                showPreviewText = true;
            }
            final Pair<CharSequence, Boolean> preview = UIHelper.getMessagePreview(activity, message, viewHolder.binding.conversationLastmsg.getCurrentTextColor());
            if (showPreviewText) {
                viewHolder.binding.conversationLastmsg.setText(EmojiWrapper.transform(UIHelper.shorten(preview.first)));
            } else {
                viewHolder.binding.conversationLastmsgImg.setContentDescription(preview.first);
            }
            viewHolder.binding.conversationLastmsg.setVisibility(showPreviewText ? View.VISIBLE : View.GONE);
            if (preview.second) {
                if (isRead) {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.ITALIC);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.NORMAL);
                } else {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD_ITALIC);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.BOLD);
                }
            } else {
                if (isRead) {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.NORMAL);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.NORMAL);
                } else {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.BOLD);
                }
            }
            if (message.getStatus() == Message.STATUS_RECEIVED) {
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    viewHolder.binding.senderName.setVisibility(View.VISIBLE);
                    viewHolder.binding.senderName.setText(UIHelper.getMessageDisplayName(message).split("\\s+")[0] + ':');
                } else {
                    viewHolder.binding.senderName.setVisibility(View.GONE);
                }
            } else if (message.getType() != Message.TYPE_STATUS) {
                viewHolder.binding.senderName.setVisibility(View.VISIBLE);
                viewHolder.binding.senderName.setText(activity.getString(R.string.me) + ':');
            } else {
                viewHolder.binding.senderName.setVisibility(View.GONE);
            }
        }

        long muted_till = conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
        if (muted_till == Long.MAX_VALUE) {
            viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
            int ic_notifications_off = activity.getThemeResource(R.attr.icon_notifications_off, R.drawable.ic_notifications_off_black_24dp);
            viewHolder.binding.notificationStatus.setImageResource(ic_notifications_off);
        } else if (muted_till >= System.currentTimeMillis()) {
            viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
            int ic_notifications_paused = activity.getThemeResource(R.attr.icon_notifications_paused, R.drawable.ic_notifications_paused_black_24dp);
            viewHolder.binding.notificationStatus.setImageResource(ic_notifications_paused);
        } else if (conversation.alwaysNotify()) {
            viewHolder.binding.notificationStatus.setVisibility(View.GONE);
        } else {
            viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
            int ic_notifications_none = activity.getThemeResource(R.attr.icon_notifications_none, R.drawable.ic_notifications_none_black_24dp);
            viewHolder.binding.notificationStatus.setImageResource(ic_notifications_none);
        }

        long timestamp;
        if (draft != null) {
            timestamp = draft.getTimestamp();
        } else {
            timestamp = conversation.getLatestMessage().getTimeSent();
        }
        viewHolder.binding.conversationLastupdate.setText(UIHelper.readableTimeDifference(activity, timestamp));
        loadAvatar(conversation, viewHolder.binding.conversationImage);
        viewHolder.itemView.setOnClickListener(v -> listener.onConversationClick(v, conversation));

        if (conversation.getMode() == Conversation.MODE_SINGLE && ShowPresenceColoredNames()) {
            switch (conversation.getContact().getPresences().getShownStatus()) {
                case CHAT:
                case ONLINE:
                    viewHolder.binding.conversationName.setTextColor(ContextCompat.getColor(activity, R.color.online));
                    break;
                case AWAY:
                    viewHolder.binding.conversationName.setTextColor(ContextCompat.getColor(activity, R.color.away));
                    break;
                case XA:
                case DND:
                    viewHolder.binding.conversationName.setTextColor(ContextCompat.getColor(activity, R.color.notavailable));
                    break;
                default:
                    viewHolder.binding.conversationName.setTextColor(StyledAttributes.getColor(activity, R.attr.text_Color_Main));
                    break;
            }
        } else {
            viewHolder.binding.conversationName.setTextColor(StyledAttributes.getColor(activity, R.attr.text_Color_Main));
        }

        if (activity.xmppConnectionService.indicateReceived()) {
            switch (message.getMergedStatus()) {
                case Message.STATUS_SEND_RECEIVED:
                    viewHolder.binding.indicatorReceived.setVisibility(View.VISIBLE);
                    break;
                case Message.STATUS_SEND_DISPLAYED:
                    viewHolder.binding.indicatorReceived.setVisibility(View.VISIBLE);
                    viewHolder.binding.indicatorRead.setVisibility(View.VISIBLE);
                    break;
                default:
                    viewHolder.binding.indicatorReceived.setVisibility(View.GONE);
                    viewHolder.binding.indicatorRead.setVisibility(View.GONE);
            }
        }
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (conversation.getIncomingChatState().equals(ChatState.COMPOSING)) {
                viewHolder.binding.conversationLastmsg.setText(R.string.is_typing);
                viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD_ITALIC);
                viewHolder.binding.senderName.setVisibility(View.GONE);
            }
        } else {
            ChatState state = ChatState.COMPOSING;
            List<MucOptions.User> userWithChatStates = conversation.getMucOptions().getUsersWithChatState(state, 5);
            if (userWithChatStates.size() == 0) {
                state = ChatState.PAUSED;
                userWithChatStates = conversation.getMucOptions().getUsersWithChatState(state, 5);
            }
            if (state == ChatState.COMPOSING) {
                if (userWithChatStates.size() > 0) {
                    if (userWithChatStates.size() == 1) {
                        MucOptions.User user = userWithChatStates.get(0);
                        viewHolder.binding.conversationLastmsg.setText(activity.getString(R.string.contact_is_typing, UIHelper.getDisplayName(user)));
                        viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD_ITALIC);
                        viewHolder.binding.senderName.setVisibility(View.GONE);
                    } else {
                        StringBuilder builder = new StringBuilder();
                        for (MucOptions.User user : userWithChatStates) {
                            if (builder.length() != 0) {
                                builder.append(", ");
                            }
                            builder.append(UIHelper.getDisplayName(user));
                        }
                        viewHolder.binding.conversationLastmsg.setText(activity.getString(R.string.contacts_are_typing, builder.toString()));
                        viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD_ITALIC);
                        viewHolder.binding.senderName.setVisibility(View.GONE);
                    }
                }
            }
        }
    }


    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public void setConversationClickListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    private void loadAvatar(Conversation conversation, ImageView imageView) {
        if (cancelPotentialWork(conversation, imageView)) {
            final Bitmap bm = activity.avatarService().get(conversation, activity.getPixel(56), true);
            if (bm != null) {
                cancelPotentialWork(conversation, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(UIHelper.getColorForName(conversation.getName().toString()));
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(conversation);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    public void insert(Conversation c, int position) {
        conversations.add(position, c);
        notifyDataSetChanged();
    }

    public void remove(Conversation conversation, int position) {
        conversations.remove(conversation);
        notifyItemRemoved(position);
    }

    public interface OnConversationClickListener {
        void onConversationClick(View view, Conversation conversation);
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final ConversationListRowBinding binding;

        private ConversationViewHolder(ConversationListRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    static class BitmapWorkerTask extends AsyncTask<Conversation, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Conversation conversation = null;

        BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }


        @Override
        protected Bitmap doInBackground(Conversation... params) {
            this.conversation = params[0];
            final XmppActivity activity = XmppActivity.find(imageViewReference);
            if (activity == null) {
                return null;
            }
            return activity.avatarService().get(this.conversation, activity.getPixel(56), isCancelled());
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && !isCancelled()) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
        }
    }

    private boolean ShowPresenceColoredNames() {
        return getPreferences().getBoolean("presence_colored_names", activity.getResources().getBoolean(R.bool.presence_colored_names));
    }

    protected SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
    }
}