/*
 * Copyright (c) 2020.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package app.openconnect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;
import app.openconnect.core.ProfileManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Terry E-mail: yaoxinghuo at 126 dot com
 * @date 2020-01-06 10:25
 * @description
 */
public class AllowedAppsActivity extends Activity {

    public static final String EXTRA_UUID = "app.openconnect.UUID";

    private ListView list;

    private AllowedAppsAdapter adapter;
    private List<PackageInfo> packageInfos = new ArrayList<>();

    private String mUUID;

    private VpnProfile mProfile;
    private Set<String> allowedApps = new HashSet<>();
    private boolean allowMode = true;
    private boolean allowByPass = false;
    private CheckBox allowModeCheckBox;
    private CheckBox allowByPassCheckBox;
    private SearchView searchView;
    private String keywords;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.allowed_apps);

        Intent intent = getIntent();
        mUUID = intent.getStringExtra(EXTRA_UUID);

        if (mUUID != null) {
            mProfile = ProfileManager.get(mUUID);
            if (mProfile != null) {
                allowedApps = mProfile.mPrefs.getStringSet("allowed_apps", new HashSet<String>());
                allowMode = !mProfile.mPrefs.getBoolean("allow_apps_are_disallowed", false);
                allowByPass = mProfile.mPrefs.getBoolean("allow_bypass", false);
            }
        }

        list = findViewById(R.id.allowed_apps);
        adapter = new AllowedAppsAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener(adapter);

        allowModeCheckBox = findViewById(R.id.allow_mode);
        allowByPassCheckBox = findViewById(R.id.allow_bypass);

        allowModeCheckBox.setChecked(allowMode);
        allowByPassCheckBox.setChecked(allowByPass);

        loadApps();

        findViewById(R.id.pb_waiting).setVisibility(View.GONE);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        searchView = (SearchView) item.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchChanged(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //it is too slow
//                searchChanged(newText);
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    public void searchChanged(String keywords) {
        this.keywords = keywords;
        loadApps();
    }

    @Override
    protected void onStop() {
        super.onStop();

        mProfile.mPrefs.edit().putStringSet("allowed_apps", allowedApps)
                .putBoolean("allow_apps_are_disallowed", !allowModeCheckBox.isChecked())
                .putBoolean("allow_bypass", allowByPassCheckBox.isChecked())
                .commit();
    }

    private void loadApps() {
        this.packageInfos.clear();
        PackageManager pm = getPackageManager();
        List<PackageInfo> infos = pm.getInstalledPackages(0);
        if (TextUtils.isEmpty(keywords)) {
            this.packageInfos.addAll(infos);
        } else {
            for (PackageInfo info : infos) {
                String packageName = info.packageName.toLowerCase();
                String name = info.applicationInfo.loadLabel(pm).toString().toLowerCase();
                if (packageName.contains(keywords) || name.contains(keywords)) {
                    this.packageInfos.add(info);
                }
            }
        }

        Collections.sort(packageInfos, new Comparator<PackageInfo>() {
            @Override
            public int compare(PackageInfo o1, PackageInfo o2) {
                Boolean contains1 = allowedApps.contains(o1.packageName);
                Boolean contains2 = allowedApps.contains(o2.packageName);
                int cp = contains1.compareTo(contains2);
                if (cp != 0) {
                    return -cp;
                }
                return o1.packageName.compareTo(o2.packageName);
            }
        });
        adapter.notifyDataSetChanged();
    }

    class AllowedAppsAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

        Context mContext;
        PackageManager pm;

        public AllowedAppsAdapter(Context mContext) {
            this.mContext = mContext;
            this.pm = mContext.getPackageManager();
        }

        @Override
        public int getCount() {
            return packageInfos.size();
        }

        @Override
        public PackageInfo getItem(int position) {
            return packageInfos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHold holder = null;
            if (convertView == null) {
                convertView = RelativeLayout.inflate(mContext, R.layout.allowed_apps_item, null);
                holder = new ViewHold(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHold) convertView.getTag();
            }

            PackageInfo packageInfo = getItem(position);

            String packageName = packageInfo.packageName;
            String appName = packageInfo.applicationInfo.loadLabel(pm).toString();
            if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                appName = "** " + appName;//indicate this is system app
            }
            holder.appName.setText(appName);
            holder.packageName.setText(packageName);
            holder.iconView.setImageDrawable(packageInfo.applicationInfo.loadIcon(pm));
            holder.checkBox.setChecked(allowedApps.contains(packageName));

            return convertView;
        }


        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            PackageInfo packageInfo = getItem(position);
            String packageName = packageInfo.packageName;
            boolean contains = allowedApps.contains(packageName);
            System.out.println("contains: " + packageName + ".c:" + contains);
            if (contains) {
                allowedApps.remove(packageName);
            } else {
                allowedApps.add(packageName);
            }
            this.notifyDataSetChanged();
        }
    }

    class ViewHold {
        TextView appName;
        TextView packageName;
        CheckBox checkBox;
        ImageView iconView;

        public ViewHold(View v) {
            appName = v.findViewById(R.id.appName);
            packageName = v.findViewById(R.id.packageName);
            checkBox = v.findViewById(R.id.check);
            iconView = v.findViewById(R.id.icon);
        }
    }

}
