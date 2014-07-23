package jp.co.gsol.oss.notifications.impl.plugin.imbox;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;

import net.arnx.jsonic.JSON;

import jp.co.gsol.oss.notifications.impl.AbstractWebSocketTask;
import jp.co.intra_mart.common.aid.jdk.java.lang.StringUtil;
import jp.co.intra_mart.foundation.context.Contexts;
import jp.co.intra_mart.foundation.context.model.AccountContext;
import jp.co.intra_mart.foundation.i18n.datetime.DateTime;
import jp.co.intra_mart.imbox.exception.IMBoxException;
import jp.co.intra_mart.imbox.model.Message;
import jp.co.intra_mart.imbox.model.Thread;
import jp.co.intra_mart.imbox.model.User;
import jp.co.intra_mart.imbox.service.MyBoxService;
import jp.co.intra_mart.imbox.service.Services;
import jp.co.intra_mart.imbox.service.UserOperations;

public class IMBoxTask extends AbstractWebSocketTask {
    private static final int MAX_INIT_WAIT = 5;

    private String messageId = null;
    private String lastMessageId = null;
    private int initWaitingCount = 0;

    @Override
    protected List<String> processedMessage(final String key,
            final Map<String, String> param) {
        final List<String> messages = new ArrayList<>();
        final String count = param.get("waitingCount");
        final String lastMid = param.get("lastMessageId");
        if (count != null)
            initWaitingCount = Integer.valueOf(count);
        if (lastMid == null && initWaitingCount < MAX_INIT_WAIT)
            return messages;
        final AccountContext ac = Contexts.get(AccountContext.class);
        final MyBoxService mbs = Services.get(MyBoxService.class);
        final UserOperations uo = Services.get(UserOperations.class);
        final Date today = DateTime.now(ac.getTimeZone()).withTime(0, 0, 0).getDate();
        System.out.println("uc:" + ac.getUserCd() + " mid:" + lastMid);
        String mid = null;
        try {
            for (Thread t : mbs.getLatestThreads(lastMid))
                for (Message m : t.getMessages()) {
                    mid = m.getMessageId();
                    if (StringUtil.isEmpty(lastMid) && m.getPostDate().before(today))
                        continue;
                    final Map<String, String> message = new HashMap<>();
                    message.put("boxName", m.getBoxName());
                    message.put("messageId", m.getMessageId());
                    message.put("postUserName", m.getPostUserName());
                    message.put("postUserCd", m.getPostUserCd());
                    message.put("message", m.getMessageText());
                    final User user = uo.getUser(m.getPostUserCd());
                    if (user != null)
                        message.put("iconId", user.getAttachId());
                    messages.add(JSON.encode(message));
                }
        } catch (IMBoxException e1) {
            // TODO 自動生成された catch ブロック
            e1.printStackTrace();
        }
        messageId = mid != null ? mid : lastMid;
        lastMessageId = lastMid != null ? lastMid : mid;
        return messages;
    }
    @Override
    protected Map<String, String> done(final String key, final boolean sent) {
        final String storeMessageId = messageId != null && sent
                ? messageId : lastMessageId != null ? lastMessageId : null;
        IMBoxMessageIdManager.messageId(key, Optional.fromNullable(storeMessageId));
            final Map<String, String> param = new HashMap<>();
        if (storeMessageId != null)
            param.put("lastMessageId", storeMessageId);
        else
            param.put("waitingCount", String.valueOf(initWaitingCount + 1));
        return param;
    }
    @Override
    protected Map<String, String> initialParam(final String key) {
        final Map<String, String> param = new HashMap<>();
        final Optional<String> mid = IMBoxMessageIdManager.messageId(key);
        if (mid.isPresent()) {
            param.put("lastMessageId", mid.get());
        }
        return param;
    }
    @Override
    protected Map<String, String> deferringParam(final String key) {
        final Optional<String> mid = IMBoxMessageIdManager.messageId(key);
        if (mid.isPresent())
            return deferringIntervalParam;
        final Map<String, String> param = new HashMap<>();
        param.put("interval", String.valueOf(5_000));
        param.put("repeate", String.valueOf(1));
        return param;
    }
}
