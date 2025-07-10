package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Stars.StarsController.findAttribute;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Rect;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class ProfileGiftsView extends View implements NotificationCenter.NotificationCenterDelegate {

    private static final int GIFT_BOUND_SIZE = dp(24);

    private final int MAX_GIFT_LIMIT = 8;
    private final int currentAccount;
    private final long dialogId;
    private final View avatarContainer;
    private final ProfileActivity.AvatarImageView avatarImage;
    private final Theme.ResourcesProvider resourcesProvider;

    public ProfileGiftsView(Context context, int currentAccount, long dialogId, @NonNull View avatarContainer, ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;

        this.avatarContainer = avatarContainer;
        this.avatarImage = avatarImage;

        this.resourcesProvider = resourcesProvider;

    }

    private float expandProgress;

    public void setExpandProgress(float progress) {
        if (this.expandProgress != progress) {
            this.expandProgress = progress;
            invalidate();
        }
    }

    private float actionBarProgress;

    public void setActionBarActionMode(float progress) {
//        if (Theme.isCurrentThemeDark()) {
//            return;
//        }
        actionBarProgress = progress;
        invalidate();
    }


    private float left, right, cy;
    private float expandY;
    private final AnimatedFloat expandRightPadAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat rightAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void setBounds(float left, float right, float cy, boolean animated) {
        boolean changed = Math.abs(left - this.left) > 0.1f || Math.abs(right - this.right) > 0.1f || Math.abs(cy - this.cy) > 0.1f;
        this.left = left;
        this.right = right;
        if (!animated) {
            this.rightAnimated.set(this.right, true);
        }
        this.cy = cy;
        if (changed) {
            invalidate();
        }
    }

    public void setExpandCoords(float yexpand) {
        this.expandY = yexpand;
        invalidate();
    }

    private float progressToInsets = 1f;

    public void setProgressToStoriesInsets(float progressToInsets) {
        if (this.progressToInsets == progressToInsets) {
            return;
        }
        this.progressToInsets = progressToInsets;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);

        for (Gift gift : gifts) {
            gift.emojiDrawable.addView(this);
        }

        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);

        for (Gift gift : gifts) {
            gift.emojiDrawable.removeView(this);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((long) args[0] == dialogId) {
                update();
            }
        }
    }

    public final class Gift {

        public final long id;
        public final TLRPC.Document document;
        public final long documentId;
        public final int color;
        public final String slug;

        public Gift(TL_stars.TL_starGiftUnique gift) {
            id = gift.id;
            document = gift.getDocument();
            documentId = document == null ? 0 : document.id;
            final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            color = backdrop.center_color | 0xFF000000;
            slug = gift.slug;
        }

        public Gift(TLRPC.TL_emojiStatusCollectible status) {
            id = status.collectible_id;
            document = null;
            documentId = status.document_id;
            color = status.center_color | 0xFF000000;
            slug = status.slug;
        }

        public boolean equals(Gift b) {
            return b != null && b.id == id;
        }

        public RadialGradient gradient;
        public final Matrix gradientMatrix = new Matrix();
        public Paint gradientPaint;
        public AnimatedEmojiDrawable emojiDrawable;
        public AnimatedFloat animatedFloat;

        public final RectF bounds = new RectF();
        public final ButtonBounce bounce = new ButtonBounce(ProfileGiftsView.this);

        public void copy(Gift b) {
            gradient = b.gradient;
            emojiDrawable = b.emojiDrawable;
            gradientPaint = b.gradientPaint;
            animatedFloat = b.animatedFloat;
        }

        public void draw(
                Canvas canvas,
                float cx, float cy,
                float ascale, float rotate,
                float alpha,
                float gradientAlpha
        ) {
            if (alpha <= 0.0f) return;
            final float gsz = dp(45);
            bounds.set(cx - gsz / 2, cy - gsz / 2, cx + gsz / 2, cy + gsz / 2);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(rotate);
            final float scale = ascale * bounce.getScale(0.1f);
            canvas.scale(scale, scale);
            if (gradientPaint != null) {
                gradientPaint.setAlpha((int) (0xFF * alpha * gradientAlpha));
                canvas.drawRect(-gsz / 2.0f, -gsz / 2.0f, gsz / 2.0f, gsz / 2.0f, gradientPaint);
            }
            if (emojiDrawable != null) {
                final int sz = GIFT_BOUND_SIZE;
                emojiDrawable.setBounds(-sz / 2, -sz / 2, sz / 2, sz / 2);
                emojiDrawable.setAlpha((int) (0xFF * alpha));
                emojiDrawable.draw(canvas);
            }
            canvas.restore();
        }
    }

    private StarsController.GiftsList list;

    public final ArrayList<Gift> oldGifts = new ArrayList<>();
    private final ArrayList<Gift> gifts = new ArrayList<>();
    private final ArrayList<GiftPosition> giftsPositions = new ArrayList<>();
    public final HashSet<Long> giftIds = new HashSet<>();
    public int maxCount;

    public void setGiftsViewBounds(Rect _rect, int circleCenterX, int circleCenterY, int circleRadius) {
        giftsPositions.clear();
        giftsPositions.addAll(distributeItems(_rect, circleCenterX, circleCenterY, circleRadius));
        invalidate();
    }

    public void update() {
        if (!MessagesController.getInstance(currentAccount).enableGiftsInProfile) {
            return;
        }

        maxCount = MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit;
        oldGifts.clear();
        oldGifts.addAll(gifts);
        gifts.clear();
        giftIds.clear();

        final TLRPC.EmojiStatus emojiStatus;
        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            emojiStatus = user == null ? null : user.emoji_status;
        } else {
            final TLRPC.User chat = MessagesController.getInstance(currentAccount).getUser(-dialogId);
            emojiStatus = chat == null ? null : chat.emoji_status;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            giftIds.add(((TLRPC.TL_emojiStatusCollectible) emojiStatus).collectible_id);
        }
        list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        if (list != null) {
            for (int i = 0; i < list.gifts.size(); i++) {
                final TL_stars.SavedStarGift savedGift = list.gifts.get(i);
                if (!savedGift.unsaved && savedGift.pinned_to_top && savedGift.gift instanceof TL_stars.TL_starGiftUnique) {
                    final Gift gift = new Gift((TL_stars.TL_starGiftUnique) savedGift.gift);
                    if (!giftIds.contains(gift.id)) {
                        gifts.add(gift);
                        giftIds.add(gift.id);
                    }
                }
            }
        }

        boolean changed = false;
        if (gifts.size() != oldGifts.size()) {
            changed = true;
        } else for (int i = 0; i < gifts.size(); i++) {
            if (!gifts.get(i).equals(oldGifts.get(i))) {
                changed = true;
                break;
            }
        }

        for (int i = 0; i < gifts.size(); i++) {
            final Gift g = gifts.get(i);
            Gift oldGift = null;
            for (int j = 0; j < oldGifts.size(); ++j) {
                if (oldGifts.get(j).id == g.id) {
                    oldGift = oldGifts.get(j);
                    break;
                }
            }

            if (oldGift != null) {
                g.copy(oldGift);
            } else {
                g.gradient = new RadialGradient(0, 0, dp(22.5f), new int[]{g.color, Theme.multAlpha(g.color, 0.0f)}, new float[]{0, 1}, Shader.TileMode.CLAMP);
                g.gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.gradientPaint.setShader(g.gradient);
                if (g.document != null) {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.document);
                } else {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.documentId);
                }
                g.animatedFloat = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
                g.animatedFloat.force(0.0f);
                if (isAttachedToWindow()) {
                    g.emojiDrawable.addView(this);
                }
            }
        }

        for (int i = 0; i < oldGifts.size(); i++) {
            final Gift g = oldGifts.get(i);
            Gift newGift = null;
            for (int j = 0; j < gifts.size(); ++j) {
                if (gifts.get(j).id == g.id) {
                    newGift = gifts.get(j);
                    break;
                }
            }
            if (newGift == null) {
                g.emojiDrawable.removeView(this);
                g.emojiDrawable = null;
                g.gradient = null;
            }
        }

        if (changed)
            invalidate();
    }


    private class GiftPosition {
        public float x, y;
        final float rotation = -8f + 16f * new java.util.Random().nextFloat(); // between -8 to 8 degree

        public GiftPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }


    private List<GiftPosition> validPositions = new ArrayList<>();

    /**
     * use this method to distribute gifts so the y do not overlap
     *
     * @param centerX
     * @param circleRadius
     * @return
     */
    private List<GiftPosition> distributeItems(
            Rect rect, float centerX, float centerY, float circleRadius) {
        validPositions = new ArrayList<>();
        float rectBottom = rect.y+ rect.height;
        float rectRight = rect.x+ rect.width;
        final float EXTRA_GAP = dp(2);
        final float boundSize = GIFT_BOUND_SIZE + EXTRA_GAP;
        final float CENTER_AVATAR_X_SAFE_ZONE = circleRadius / 2;

        final int TOOLBAR_BACK_BUTTON_END_Y = AndroidUtilities.dp(48) + AndroidUtilities.statusBarHeight;
        final int TOOLBAR_BACK_BUTTON_END_X = AndroidUtilities.dp(48) ;

        Random random = new Random();

        // 1. Generate all valid positions
        for (float y = rect.y + boundSize/2; y <= rectBottom - boundSize/2; y += boundSize) {
            if(y<AndroidUtilities.statusBarHeight) continue;
            for (float x = rect.x + boundSize/2 + random.nextInt((int)boundSize/2); x <= rectRight - boundSize/2; x += boundSize) {
                if(y < TOOLBAR_BACK_BUTTON_END_Y && x < TOOLBAR_BACK_BUTTON_END_X) continue; //should not paint on back button
                if(x < TOOLBAR_BACK_BUTTON_END_X) continue; //should not paint on back button
                if(y - rect.y < TOOLBAR_BACK_BUTTON_END_Y && rect.width - x < TOOLBAR_BACK_BUTTON_END_X) continue; //should not paint on setting icon at right
                if(x > centerX - CENTER_AVATAR_X_SAFE_ZONE - boundSize && x < centerX + CENTER_AVATAR_X_SAFE_ZONE + boundSize/2) continue; //should not draw near center of avatar image for better visual
                float dx = x - centerX;
                float dy = y - centerY;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (dist < circleRadius + boundSize/2) continue;
                validPositions.add(new GiftPosition(x, y));
            }
        }
        // 2. Split into left and right, sort by distance to center
        List<GiftPosition> left = new ArrayList<>();
        List<GiftPosition> right = new ArrayList<>();
        for (GiftPosition pos : validPositions) {
            if (pos.x < centerX)
                left.add(pos);
            else
                right.add(pos);
        }

        // 3. Alternate assignment : one left, one right ...
        List<GiftPosition> result = new ArrayList<>();
        List<GiftPosition> leftResults = new ArrayList<>();
        List<GiftPosition> rightResults = new ArrayList<>();
        int l = 0, r = 0;
        for (int i = 0; i < MAX_GIFT_LIMIT; i++) {
            if (i % 2 == 0 && l < left.size()) {
                int randomIndex = random.nextInt(left.size());
                leftResults.add(left.get(randomIndex));
                left.remove(randomIndex);
                l++;
            } else if (i % 2 == 1 && r < right.size()) {
                int randomIndex = random.nextInt(right.size());
                rightResults.add(right.get(randomIndex));
                right.remove(randomIndex);
                r++;
            }
        }

        // 4. Sort based on y, so the items at bottom move first during animation to not overlap with texts
        leftResults.sort(Comparator.comparingDouble(pos -> pos.y));
        rightResults.sort(Comparator.comparingDouble(pos -> pos.y));
        l = 0;
        r = 0;
        for (int i = 0; i < MAX_GIFT_LIMIT; i++) {
            if (i % 2 == 0 && l < left.size()) {
                result.add(leftResults.get(l));
                l++;
            } else if (i % 2 == 1 && r < right.size()) {
                result.add(rightResults.get(r));
                r++;
            }
        }
        return result;
    }

    public final AnimatedFloat animatedCount = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (gifts.isEmpty() || giftsPositions.isEmpty() || expandProgress > 1.0f) return;

//        TestPaint.setColor(Theme.getColor(Theme.key_avatar_nameInMessageRed));

        final float avatarCenterX = avatarContainer.getX() + (avatarContainer.getWidth() * avatarContainer.getScaleX() / 2);
        final float avatarCenterY = avatarContainer.getY() + (avatarContainer.getHeight() * avatarContainer.getScaleY() / 2);

        canvas.save();
        canvas.clipRect(0, 0, getWidth(), expandY); //todo expand y logic should improve

        final float stagger = 0.1f; // delay between each gift item to start moving
        final int count = Math.min(giftsPositions.size(), gifts.size());
        final float maxOffset = (count - 1) * stagger;

        for (int i = 0; i < count; ++i) {

            float giftProgress = (expandProgress - i * stagger) / (1.0f - maxOffset);
            giftProgress = Math.max(0f, Math.min(1f, giftProgress * 1.3f)); // clamp between 0 and 1
            final GiftPosition position = giftsPositions.get(i);
            final Gift gift = gifts.get(i);

            final float scale = lerp((i + 1) * 0.1f, 1.0f, giftProgress);
            float x = AndroidUtilities.lerp(avatarCenterX, position.x, giftProgress);
            float y = AndroidUtilities.lerp(avatarCenterY, position.y, giftProgress);
            gift.draw(
                    canvas,
                    x,
                    y,
                    scale,
                    position.rotation,
                    1.0f,
                    1.0f
            );
        }

        canvas.restore();
    }

    public Gift getGiftUnder(float x, float y) {
        for (int i = 0; i < gifts.size(); ++i) {
            if (gifts.get(i).bounds.contains(x, y))
                return gifts.get(i);
        }
        return null;
    }

    private Gift pressedGift;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final Gift hit = getGiftUnder(event.getX(), event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressedGift = hit;
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(true);
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressedGift != hit && pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressedGift != null) {
                onGiftClick(pressedGift);
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        }
        return pressedGift != null;
    }

    public void onGiftClick(Gift gift) {
        Browser.openUrl(getContext(), "https://t.me/nft/" + gift.slug);
    }
}
