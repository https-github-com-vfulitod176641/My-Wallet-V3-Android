package info.blockchain.wallet;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Looper;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.dm.zbar.android.scanner.ZBarConstants;
import com.dm.zbar.android.scanner.ZBarScannerActivity;
import com.google.bitcoin.core.Base58;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.android.Contents;
import com.google.zxing.client.android.encode.QRCodeEncoder;

import net.sourceforge.zbar.Symbol;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import info.blockchain.wallet.multiaddr.MultiAddrFactory;
import info.blockchain.wallet.payload.Account;
import info.blockchain.wallet.payload.ImportedAccount;
import info.blockchain.wallet.payload.LegacyAddress;
import info.blockchain.wallet.payload.PayloadFactory;
import info.blockchain.wallet.payload.ReceiveAddress;
import info.blockchain.wallet.util.AppUtil;
import info.blockchain.wallet.util.CharSequenceX;
import info.blockchain.wallet.util.DoubleEncryptionFactory;
import info.blockchain.wallet.util.MonetaryUtil;
import info.blockchain.wallet.util.PrefsUtil;
import info.blockchain.wallet.util.PrivateKeyFactory;
import info.blockchain.wallet.util.TypefaceUtil;

public class MyAccountsActivity extends Activity {

    private static final int IMPORT_PRIVATE_KEY = 2006;

    public static String ACCOUNT_HEADER = "";
    public static String IMPORTED_HEADER = "";

    private LinearLayoutManager layoutManager = null;
    private RecyclerView mRecyclerView = null;
    private List<MyAccountItem> accountsAndImportedList = null;
    private TextView myAccountsHeader;
    private float originalHeaderTextSize;
    public int toolbarHeight;

    private ImageView backNav;
    private ImageView menuImport;
    private HashMap<View,Boolean> rowViewState;

    private ArrayList<Integer> headerPositions;
    private int hdAccountsIdx;
    private List<LegacyAddress> legacy = null;

    private ProgressDialog progress = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_my_accounts);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        ACCOUNT_HEADER = getResources().getString(R.string.accounts);
        IMPORTED_HEADER = getResources().getString(R.string.imported_addresses);

        backNav = (ImageView)findViewById(R.id.back_nav);
        backNav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        menuImport = (ImageView)findViewById(R.id.menu_import);
        menuImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String[] list = new String[] {getResources().getString(R.string.import_address)};
                ArrayAdapter<String> popupAdapter = new ArrayAdapter<String>(MyAccountsActivity.this,R.layout.spinner_item2, list);

                final ListPopupWindow menuPopup = new ListPopupWindow(MyAccountsActivity.this,null);
                menuPopup.setAnchorView(menuImport);
                menuPopup.setAdapter(popupAdapter);
                menuPopup.setModal(true);
                menuPopup.setAnimationStyle(R.anim.slide_down1);
                menuPopup.setContentWidth(measureContentWidth(popupAdapter));//always size to max width item
                menuPopup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        switch (position) {
                            case 0:
                                scanPrivateKey();
                                break;
                        }

                        if (menuPopup.isShowing()) menuPopup.dismiss();
                    }
                });
                menuPopup.show();
            }

            private int measureContentWidth(ListAdapter listAdapter) {
                ViewGroup mMeasureParent = null;
                int maxWidth = 0;
                View itemView = null;
                int itemType = 0;

                final ListAdapter adapter = listAdapter;
                final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                final int count = adapter.getCount();
                for (int i = 0; i < count; i++) {
                    final int positionType = adapter.getItemViewType(i);
                    if (positionType != itemType) {
                        itemType = positionType;
                        itemView = null;
                    }

                    if (mMeasureParent == null) {
                        mMeasureParent = new FrameLayout(MyAccountsActivity.this);
                    }

                    itemView = adapter.getView(i, itemView, mMeasureParent);
                    itemView.measure(widthMeasureSpec, heightMeasureSpec);

                    final int itemWidth = itemView.getMeasuredWidth();

                    if (itemWidth > maxWidth) {
                        maxWidth = itemWidth;
                    }
                }

                return maxWidth;
            }

        });

        myAccountsHeader = (TextView)findViewById(R.id.my_accounts_heading);
        myAccountsHeader.setTypeface(TypefaceUtil.getInstance(this).getRobotoTypeface());
        originalHeaderTextSize = myAccountsHeader.getTextSize();

        mRecyclerView = (RecyclerView)findViewById(R.id.accountsList);
        layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);

        headerPositions = new ArrayList<Integer>();

        ArrayList<MyAccountItem> accountItems = new ArrayList<>();
        //First Header Position
        headerPositions.add(0);
        accountItems.add(new MyAccountItem(ACCOUNT_HEADER,"",getResources().getDrawable(R.drawable.icon_accounthd)));

        accountsAndImportedList = getAccounts();

        toolbarHeight = (int)getResources().getDimension(R.dimen.action_bar_height)+35;

        for(MyAccountItem item : accountsAndImportedList){
            accountItems.add(item);
        }

        MyAccountsAdapter accountsAdapter = new MyAccountsAdapter(accountItems);
        mRecyclerView.setAdapter(accountsAdapter);

        rowViewState = new HashMap<View, Boolean>();
        mRecyclerView.addOnItemTouchListener(
                new RecyclerItemClickListener(this, new RecyclerItemClickListener.OnItemClickListener() {

                    private int originalHeight = 0;
                    private int newHeight = 0;
                    private int expandDuration = 200;
                    private boolean mIsViewExpanded = false;

                    @Override
                    public void onItemClick(final View view, int position) {

                        if (headerPositions.contains(position)) return;//headers unclickable

                        try {
                            mIsViewExpanded = rowViewState.get(view);
                        } catch (Exception e) {
                            mIsViewExpanded = false;
                        }

                        final ImageView qrTest = (ImageView) view.findViewById(R.id.qrr);
                        final TextView addressView = (TextView)view.findViewById(R.id.my_account_row_address);

                        //Receiving Address
                        String currentSelectedAddress = null;

                        if (position-2 >= hdAccountsIdx)//2 headers before imported
                            currentSelectedAddress = legacy.get(position-2 - hdAccountsIdx).getAddress();
                        else {
                            ReceiveAddress currentSelectedReceiveAddress = null;
                            try {
                                currentSelectedReceiveAddress = HDPayloadBridge.getInstance(MyAccountsActivity.this).getReceiveAddress(position-1);//1 header before accounts
                                currentSelectedAddress = currentSelectedReceiveAddress.getAddress();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        addressView.setText(currentSelectedAddress);

                        //Receiving QR
                        qrTest.setImageBitmap(generateQRCode(BitcoinURI.convertToBitcoinURI(currentSelectedAddress, BigInteger.ZERO, "", "")));

                        if (originalHeight == 0) {
                            originalHeight = view.getHeight();
                        }

                        newHeight = originalHeight + qrTest.getHeight() + (addressView.getHeight()*2)+(16*2);

                        final String finalCurrentSelectedAddress = currentSelectedAddress;
                        qrTest.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {

                                new AlertDialog.Builder(MyAccountsActivity.this)
                                        .setTitle(R.string.app_name)
                                        .setMessage(R.string.receive_address_to_clipboard)
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                                            public void onClick(DialogInterface dialog, int whichButton) {

                                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)MyAccountsActivity.this.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                                android.content.ClipData clip = null;
                                                clip = android.content.ClipData.newPlainText("Send address", finalCurrentSelectedAddress);
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(MyAccountsActivity.this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();

                                            }

                                        }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        ;
                                    }
                                }).show();

                                return false;
                            }
                        });

                        ValueAnimator headerResizeAnimator;
                        if (!mIsViewExpanded) {
                            //Expanding
                            view.setBackgroundColor(getResources().getColor(R.color.white));

                            //Fade QR in - expansion of row will create slide down effect
                            qrTest.setVisibility(View.VISIBLE);
                            qrTest.setAnimation(AnimationUtils.loadAnimation(MyAccountsActivity.this, R.anim.abc_fade_in));
                            qrTest.setEnabled(true);

                            addressView.setVisibility(View.VISIBLE);
                            Animation aanim = AnimationUtils.loadAnimation(MyAccountsActivity.this, R.anim.abc_fade_in);
                            aanim.setDuration(expandDuration);
                            addressView.setAnimation(aanim);
                            addressView.setEnabled(true);

                            mIsViewExpanded = !mIsViewExpanded;
                            view.findViewById(R.id.bottom_seperator).setVisibility(View.VISIBLE);
                            view.findViewById(R.id.top_seperator).setVisibility(View.VISIBLE);
                            headerResizeAnimator = ValueAnimator.ofInt(originalHeight, newHeight);

                        } else {
                            //Collapsing
                            TypedValue outValue = new TypedValue();
                            MyAccountsActivity.this.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                            view.setBackgroundResource(outValue.resourceId);

                            view.findViewById(R.id.bottom_seperator).setVisibility(View.INVISIBLE);
                            view.findViewById(R.id.top_seperator).setVisibility(View.INVISIBLE);
                            mIsViewExpanded = !mIsViewExpanded;
                            headerResizeAnimator = ValueAnimator.ofInt(newHeight, originalHeight);

                            //Slide QR away
                            qrTest.setAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_down));
                            Animation aanim = AnimationUtils.loadAnimation(MyAccountsActivity.this, R.anim.abc_fade_out);
                            aanim.setDuration(expandDuration/2);
                            addressView.setAnimation(aanim);

                            //Fade QR and hide when done
                            Animation anim = new AlphaAnimation(1.00f, 0.00f);
                            anim.setDuration(expandDuration/2);
                            // Set a listener to the animation and configure onAnimationEnd
                            anim.setAnimationListener(new Animation.AnimationListener() {
                                @Override
                                public void onAnimationStart(Animation animation) {

                                }

                                @Override
                                public void onAnimationEnd(Animation animation) {
                                    qrTest.setVisibility(View.INVISIBLE);
                                    qrTest.setEnabled(false);

                                    addressView.setVisibility(View.INVISIBLE);
                                    addressView.setEnabled(false);
                                }

                                @Override
                                public void onAnimationRepeat(Animation animation) {

                                }
                            });

                            qrTest.startAnimation(anim);
                            addressView.startAnimation(anim);
                        }

                        //Set and start row collapse/expand
                        headerResizeAnimator.setDuration(expandDuration);
                        headerResizeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                        headerResizeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            public void onAnimationUpdate(ValueAnimator animation) {
                                Integer value = (Integer) animation.getAnimatedValue();
                                view.getLayoutParams().height = value.intValue();
                                view.requestLayout();
                            }
                        });


                        headerResizeAnimator.start();

                        rowViewState.put(view,mIsViewExpanded);
                    }
                })
        );

        mRecyclerView.setOnScrollListener(new CollapseActionbarScrollListener() {
            @Override
            public void onMoved(int distance, float scaleFactor) {

                myAccountsHeader.setTranslationY(-distance);

                if (scaleFactor >= 0.7 && scaleFactor <= 1) {
                    float mm = (originalHeaderTextSize * scaleFactor);
                    myAccountsHeader.setTextSize(TypedValue.COMPLEX_UNIT_PX, mm);
                }
            }
        });
    }

    private List<MyAccountItem> getAccounts() {

        List<MyAccountItem> accountList = new ArrayList<MyAccountItem>();
        ImportedAccount iAccount = null;

        List<Account> accounts = PayloadFactory.getInstance().get().getHdWallet().getAccounts();
        if(PayloadFactory.getInstance().get().getLegacyAddresses().size() > 0) {
            iAccount = new ImportedAccount(getString(R.string.imported_addresses), PayloadFactory.getInstance().get().getLegacyAddresses(), new ArrayList<String>(), MultiAddrFactory.getInstance().getLegacyBalance());
        }

        List<Account> accountClone = new ArrayList<Account>(accounts.size());
        accountClone.addAll(accounts);

        if(accountClone.get(accountClone.size() - 1) instanceof ImportedAccount) {
            accountClone.remove(accountClone.size() - 1);
        }
        hdAccountsIdx = accountClone.size();

        int i = 0;
        for(; i < accountClone.size(); i++) {

            String label = accountClone.get(i).getLabel();
            if(label==null || label.length() == 0)label = "Account: " + (i + 1);

            accountList.add(new MyAccountItem(label,displayBalance(i), getResources().getDrawable(R.drawable.icon_accounthd)));
        }

        if(iAccount != null) {

            //Imported Header Position
            headerPositions.add(i+1);
            accountList.add(new MyAccountItem(IMPORTED_HEADER,"", getResources().getDrawable(R.drawable.icon_accounthd)));

            legacy = iAccount.getLegacyAddresses();
            for(int j = 0; j < legacy.size(); j++) {

                String label = legacy.get(j).getLabel();
                if(label==null || label.length() == 0)label = legacy.get(j).getAddress();

                accountList.add(new MyAccountItem(label,displayBalanceImported(j),getResources().getDrawable(R.drawable.icon_imported)));
            }
        }

        return accountList;
    }

    private String displayBalance(int index) {

        String address = HDPayloadBridge.getInstance(this).account2Xpub(index);
        Long amount = MultiAddrFactory.getInstance().getXpubAmounts().get(address);
        if(amount==null)amount = 0l;

        String unit = (String) MonetaryUtil.getInstance().getBTCUnits()[PrefsUtil.getInstance(this).getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC)];

        return MonetaryUtil.getInstance(MyAccountsActivity.this).getDisplayAmount(amount) + " " + unit;
    }

    private String displayBalanceImported(int index) {

        String address = legacy.get(index).getAddress();
        Long amount = MultiAddrFactory.getInstance().getLegacyBalance(address);
        if(amount==null)amount = 0l;
        String unit = (String) MonetaryUtil.getInstance().getBTCUnits()[PrefsUtil.getInstance(this).getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC)];

        return MonetaryUtil.getInstance(MyAccountsActivity.this).getDisplayAmount(amount) + " " + unit;
    }

    public abstract class CollapseActionbarScrollListener extends RecyclerView.OnScrollListener {

        private int mToolbarOffset = 0;
        private float scaleFactor = 1;

        public CollapseActionbarScrollListener() {
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            //Only bring heading back down after 2nd item visible (0 = heading)
            if (layoutManager.findFirstCompletelyVisibleItemPosition() <= 2) {

                if ((mToolbarOffset < toolbarHeight && dy > 0) || (mToolbarOffset > 0 && dy < 0)) {
                    mToolbarOffset += dy;
                    scaleFactor = (float) ((toolbarHeight*2) - mToolbarOffset) / (float) (toolbarHeight*2);
                }

                clipToolbarOffset();
                onMoved(mToolbarOffset, scaleFactor);
            }
        }

        private void clipToolbarOffset() {
            if(mToolbarOffset > toolbarHeight) {
                mToolbarOffset = toolbarHeight;
            } else if(mToolbarOffset < 0) {
                mToolbarOffset = 0;
                scaleFactor = 0.7f;
            }
        }

        public abstract void onMoved(int distance, float scaleFactor);
    }

    private Bitmap generateQRCode(String uri) {

        Bitmap bitmap = null;
        int qrCodeDimension = 260;

        QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(uri, null, Contents.Type.TEXT, BarcodeFormat.QR_CODE.toString(), qrCodeDimension);

        try {
            bitmap = qrCodeEncoder.encodeAsBitmap();
        } catch (WriterException e) {
            e.printStackTrace();
        }

        return bitmap;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(resultCode == Activity.RESULT_OK && requestCode == IMPORT_PRIVATE_KEY
                && data != null && data.getStringExtra(ZBarConstants.SCAN_RESULT) != null)	{
            try	{
                final String strResult = data.getStringExtra(ZBarConstants.SCAN_RESULT);
                String format = PrivateKeyFactory.getInstance().getFormat(strResult);
                if(format != null)	{
                    if(!format.equals(PrivateKeyFactory.BIP38))	{
                        importNonBIP38Address(format, strResult);
                    }
                    else	{
                        importBIP38Address(strResult);
                    }

                    updateAmounts();

                }
                else	{
                    Toast.makeText(MyAccountsActivity.this, R.string.privkey_error, Toast.LENGTH_SHORT).show();
                }
            }
            catch(Exception e)	{
                Toast.makeText(MyAccountsActivity.this, R.string.privkey_error, Toast.LENGTH_SHORT).show();
            }
        }
        else if(resultCode == Activity.RESULT_CANCELED && requestCode == IMPORT_PRIVATE_KEY)	{
            ;
        }

    }

    private void scanPrivateKey() {

        if(!PayloadFactory.getInstance().get().isDoubleEncrypted()) {
            Intent intent = new Intent(MyAccountsActivity.this, ZBarScannerActivity.class);
            intent.putExtra(ZBarConstants.SCAN_MODES, new int[]{ Symbol.QRCODE } );
            startActivityForResult(intent, IMPORT_PRIVATE_KEY);
        }
        else {
            final EditText double_encrypt_password = new EditText(MyAccountsActivity.this);
            double_encrypt_password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            new AlertDialog.Builder(MyAccountsActivity.this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.enter_double_encryption_pw)
                    .setView(double_encrypt_password)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                            String pw2 = double_encrypt_password.getText().toString();

                            if(pw2 != null && pw2.length() > 0 && DoubleEncryptionFactory.getInstance().validateSecondPassword(
                                    PayloadFactory.getInstance().get().getDoublePasswordHash(),
                                    PayloadFactory.getInstance().get().getSharedKey(),
                                    new CharSequenceX(pw2),
                                    PayloadFactory.getInstance().get().getIterations()
                            )) {

                                PayloadFactory.getInstance().setTempDoubleEncryptPassword(new CharSequenceX(pw2));

                                Intent intent = new Intent(MyAccountsActivity.this, ZBarScannerActivity.class);
                                intent.putExtra(ZBarConstants.SCAN_MODES, new int[]{ Symbol.QRCODE } );
                                startActivityForResult(intent, IMPORT_PRIVATE_KEY);

                            }
                            else {
                                Toast.makeText(MyAccountsActivity.this, R.string.double_encryption_password_error, Toast.LENGTH_SHORT).show();
                                PayloadFactory.getInstance().setTempDoubleEncryptPassword(new CharSequenceX(""));
                            }

                        }
                    }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    ;
                }
            }).show();
        }

    }

    private void importBIP38Address(final String data)	{

        final EditText password = new EditText(this);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.password_entry)
                .setView(password)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        final String pw = password.getText().toString();

                        if(progress != null && progress.isShowing()) {
                            progress.dismiss();
                            progress = null;
                        }
                        progress = new ProgressDialog(MyAccountsActivity.this);
                        progress.setTitle(R.string.app_name);
                        progress.setMessage(MyAccountsActivity.this.getResources().getString(R.string.please_wait));
                        progress.show();

                        new Thread(new Runnable() {
                            @Override
                            public void run() {

                                Looper.prepare();

                                try {
                                    final ECKey key = PrivateKeyFactory.getInstance().getKey(PrivateKeyFactory.BIP38, data, new CharSequenceX(pw));
                                    if(key != null && key.hasPrivKey() && !PayloadFactory.getInstance().get().getLegacyAddressStrings().contains(key.toAddress(MainNetParams.get()).toString()))	{
                                        final LegacyAddress legacyAddress = new LegacyAddress(null, System.currentTimeMillis() / 1000L, key.toAddress(MainNetParams.get()).toString(), "", 0L, "android", "");
									        		/*
									        		 * if double encrypted, save encrypted in payload
									        		 */
                                        if(!PayloadFactory.getInstance().get().isDoubleEncrypted())	{
                                            legacyAddress.setEncryptedKey(key.getPrivKeyBytes());
                                        }
                                        else	{
                                            String encryptedKey = new String(Base58.encode(key.getPrivKeyBytes()));
                                            String encrypted2 = DoubleEncryptionFactory.getInstance().encrypt(encryptedKey, PayloadFactory.getInstance().get().getSharedKey(), PayloadFactory.getInstance().getTempDoubleEncryptPassword().toString(), PayloadFactory.getInstance().get().getIterations());
                                            legacyAddress.setEncryptedKey(encrypted2);
                                        }

                                        final EditText address_label = new EditText(MyAccountsActivity.this);

                                        new AlertDialog.Builder(MyAccountsActivity.this)
                                                .setTitle(R.string.app_name)
                                                .setMessage(R.string.label_address)
                                                .setView(address_label)
                                                .setCancelable(false)
                                                .setPositiveButton(R.string.save_name, new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int whichButton) {
                                                        String label = address_label.getText().toString();
                                                        if(label != null && label.length() > 0) {
                                                            legacyAddress.setLabel(label);
                                                        }
                                                        else {
                                                            legacyAddress.setLabel("");
                                                        }
                                                        PayloadFactory.getInstance().get().getLegacyAddresses().add(legacyAddress);
                                                        Toast.makeText(getApplicationContext(), key.toAddress(MainNetParams.get()).toString(), Toast.LENGTH_SHORT).show();
                                                        PayloadFactory.getInstance(MyAccountsActivity.this).remoteSaveThread();

                                                        MyAccountsActivity.this.recreate();
                                                    }
                                                }).setNegativeButton(R.string.polite_no, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                legacyAddress.setLabel("");
                                                PayloadFactory.getInstance().get().getLegacyAddresses().add(legacyAddress);
                                                Toast.makeText(getApplicationContext(), key.toAddress(MainNetParams.get()).toString(), Toast.LENGTH_SHORT).show();
                                                PayloadFactory.getInstance(MyAccountsActivity.this).remoteSaveThread();

                                                MyAccountsActivity.this.recreate();
                                            }
                                        }).show();

                                    }
                                    else	{
                                        Toast.makeText(MyAccountsActivity.this, R.string.bip38_error, Toast.LENGTH_SHORT).show();
                                    }
                                }
                                catch(Exception e) {
                                    Toast.makeText(MyAccountsActivity.this, R.string.invalid_password, Toast.LENGTH_SHORT).show();
                                }
                                finally {
                                    if(progress != null && progress.isShowing()) {
                                        progress.dismiss();
                                        progress = null;
                                    }
                                }

                                Looper.loop();

                            }
                        }).start();

                    }
                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                AppUtil.getInstance(MyAccountsActivity.this).restartApp();
            }
        }).show();
    }

    private void importNonBIP38Address(final String format, final String data)	{

        ECKey key = null;

        try	{
            key = PrivateKeyFactory.getInstance().getKey(format, data);
        }
        catch(Exception e)	{
            e.printStackTrace();
            return;
        }

        if(key != null && key.hasPrivKey() && !PayloadFactory.getInstance().get().getLegacyAddressStrings().contains(key.toAddress(MainNetParams.get()).toString()))	{
            final LegacyAddress legacyAddress = new LegacyAddress(null, System.currentTimeMillis() / 1000L, key.toAddress(MainNetParams.get()).toString(), "", 0L, "android", "");
			/*
			 * if double encrypted, save encrypted in payload
			 */
            if(!PayloadFactory.getInstance().get().isDoubleEncrypted())	{
                legacyAddress.setEncryptedKey(key.getPrivKeyBytes());
            }
            else	{
                String encryptedKey = new String(Base58.encode(key.getPrivKeyBytes()));
                String encrypted2 = DoubleEncryptionFactory.getInstance().encrypt(encryptedKey, PayloadFactory.getInstance().get().getSharedKey(), PayloadFactory.getInstance().getTempDoubleEncryptPassword().toString(), PayloadFactory.getInstance().get().getIterations());
                legacyAddress.setEncryptedKey(encrypted2);
            }

            final EditText address_label = new EditText(MyAccountsActivity.this);

            final ECKey scannedKey = key;

            new AlertDialog.Builder(MyAccountsActivity.this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.label_address)
                    .setView(address_label)
                    .setCancelable(false)
                    .setPositiveButton(R.string.save_name, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            String label = address_label.getText().toString();
                            if(label != null && label.length() > 0) {
                                legacyAddress.setLabel(label);
                            }
                            else {
                                legacyAddress.setLabel("");
                            }
                            PayloadFactory.getInstance().get().getLegacyAddresses().add(legacyAddress);
                            Toast.makeText(getApplicationContext(), scannedKey.toAddress(MainNetParams.get()).toString(), Toast.LENGTH_SHORT).show();
                            PayloadFactory.getInstance(MyAccountsActivity.this).remoteSaveThread();

                            MyAccountsActivity.this.recreate();
                        }
                    }).setNegativeButton(R.string.polite_no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    legacyAddress.setLabel("");
                    PayloadFactory.getInstance().get().getLegacyAddresses().add(legacyAddress);
                    Toast.makeText(getApplicationContext(), scannedKey.toAddress(MainNetParams.get()).toString(), Toast.LENGTH_SHORT).show();
                    PayloadFactory.getInstance(MyAccountsActivity.this).remoteSaveThread();

                    MyAccountsActivity.this.recreate();
                }
            }).show();

        }
        else	{
            Toast.makeText(MyAccountsActivity.this, getString(R.string.no_private_key), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAmounts()	{

        new Thread(new Runnable() {
            @Override
            public void run() {

                Looper.prepare();

                List<String> legacies = PayloadFactory.getInstance().get().getLegacyAddressStrings();
                String[] s = legacies.toArray(new String[legacies.size()]);
                MultiAddrFactory.getInstance().getLegacy(s, false);

                headerPositions = new ArrayList<Integer>();

                ArrayList<MyAccountItem> accountItems = new ArrayList<>();
                //First Header Position
                headerPositions.add(0);
                accountItems.add(new MyAccountItem(ACCOUNT_HEADER,"",getResources().getDrawable(R.drawable.icon_accounthd)));

                accountsAndImportedList = getAccounts();

                toolbarHeight = (int)getResources().getDimension(R.dimen.action_bar_height)+35;

                for(MyAccountItem item : accountsAndImportedList){
                    accountItems.add(item);
                }
/*
                MyAccountsAdapter accountsAdapter = new MyAccountsAdapter(accountItems);
                mRecyclerView.setAdapter(accountsAdapter);
                accountsAdapter.notifyDataSetChanged();
*/
                Looper.loop();

            }
        }).start();
    }

}