package com.dylanz.luckydog.service;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.annotation.TargetApi;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.List;

public class LuckyService extends AccessibilityService {
    /**
     * 日志的 tag，随意
     */
    public static final String TAG = "LuckyService";

    /**
     * 红包是否打开的状态记录变量
     */
    private boolean isRedPacketOpened = false;

    /**
     * 红包消息辨别关键字
     */
    private static final String HONG_BAO_TXT = "[微信红包]";

    /**
     * 有"开"的那个小弹窗的 className
     */
    private static final String ACTIVITY_DIALOG_LUCKY_MONEY = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotHookReceiveUI";

    /**
     * 红包领取后的详情页面的 className
     */
    private static final String LUCKY_MONEY_DETAIL = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";

    /**
     * 收到的红包，整个控件的 id
     */
    private static final String RED_PACKET_ID = "com.tencent.mm:id/tv";

    /**
     * 已领过的红包有个"已领取"字眼，这个字眼对应的控件 id
     */
    private static final String OPENED_ID = "com.tencent.mm:id/tt";

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        //获取当前界面包名
        CharSequence packageNameChar = event.getPackageName();
        Log.e(TAG, "packageNameChar：" + packageNameChar);
        if (packageNameChar == null || !packageNameChar.toString().equals("com.tencent.mm")) {
            return;
        }
        isRedPacketOpened = false;
        //获取当前类名
        String className = event.getClassName().toString();

        //红包领取后的详情页面，自动返回
        if (className.equals(LUCKY_MONEY_DETAIL)) {
            Log.e(TAG, "红包已领取，返回聊天页面：");
            performGlobalAction(GLOBAL_ACTION_BACK);
            return;
        }

        //当前为红包弹出窗（有"开"的那个小弹窗）
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && className.equals(ACTIVITY_DIALOG_LUCKY_MONEY)) {
            //有可能会由于网络原因，"开"的那个小弹框会需要加载后才显示，我们此处最多等 5 秒钟
            for (int i = 0; i < 1000; i++) {
                if (isRedPacketOpened) {
                    break;
                }
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                openRedPacket(rootNode);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return;
        }

        //遍历消息列表的每个消息，点击红包控件
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        jumpIntoRedPacket(nodeInfo);

        //是否微信聊天页面的类
        if (event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
            Log.e(TAG, "接收到通知栏消息");
            Notification notification = (Notification) event.getParcelableData();
            //获取通知消息详情
            if (notification.tickerText == null) {
                return;
            }
            String content = notification.tickerText.toString();
            //解析消息
            String[] msg = content.split(":");
            if (msg.length == 0) {
                return;
            }
            String text = msg[1].trim();
            if (text.contains(HONG_BAO_TXT)) {
                Log.e(TAG, "接收到通知栏红包消息，点击消息，进入聊天界面");
                //打开通知栏的intent，即打开对应的聊天界面
                PendingIntent pendingIntent = notification.contentIntent;
                try {
                    pendingIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 点击"开"按钮
     *
     * @param rootNode rootNode
     */
    private void openRedPacket(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            return;
        }
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            AccessibilityNodeInfo node = rootNode.getChild(i);
            if ("android.widget.Button".equals(node.getClassName().toString())) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                isRedPacketOpened = true;
                break;
            }
            openRedPacket(node);
        }
    }

    @Override
    public void onInterrupt() {

    }

    /**
     * 点击"开"按钮
     *
     * @param rootNode rootNode
     */
    private void jumpIntoRedPacket(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            return;
        }
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            //获取到子控件
            AccessibilityNodeInfo node = rootNode.getChild(i);
            //获取红包控件
            AccessibilityNodeInfo target = findViewByID(node, RED_PACKET_ID);
            if (target != null) {
                //已领取这个控件为空，红包还没有被领取
                if (findViewByID(node, OPENED_ID) == null) {
                    Log.e(TAG, "找到未领取的红包，点击红包");
                    performViewClick(target);
                    break;
                }
            }
            jumpIntoRedPacket(node);
        }
    }

    /**
     * 模拟点击事件
     *
     * @param nodeInfo nodeInfo
     */
    public void performViewClick(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return;
        }
        while (nodeInfo != null) {
            Log.e(TAG, "打开红包1");
            if (nodeInfo.isClickable()) {
                Log.e(TAG, "打开红包2");
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                break;
            }
            nodeInfo = nodeInfo.getParent();
        }
    }

    /**
     * 查找对应ID的View
     *
     * @param accessibilityNodeInfo AccessibilityNodeInfo
     * @param id                    id
     * @return View
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public AccessibilityNodeInfo findViewByID(AccessibilityNodeInfo accessibilityNodeInfo, String id) {
        if (accessibilityNodeInfo == null) {
            return null;
        }
        List<AccessibilityNodeInfo> nodeInfoList = accessibilityNodeInfo.findAccessibilityNodeInfosByViewId(id);
        if (nodeInfoList != null && !nodeInfoList.isEmpty()) {
            for (AccessibilityNodeInfo nodeInfo : nodeInfoList) {
                if (nodeInfo != null) {
                    return nodeInfo;
                }
            }
        }
        return null;
    }
}