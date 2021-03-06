package m.nischal.melody.ui;

/*The MIT License (MIT)
 *
 *    Copyright (c) 2015 Nischal M
 *
 *    Permission is hereby granted, free of charge, to any person obtaining a copy
 *    of this software and associated documentation files (the "Software"), to deal
 *    in the Software without restriction, including without limitation the rights
 *    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *    copies of the Software, and to permit persons to whom the Software is
 *    furnished to do so, subject to the following conditions:
 *
 *    The above copyright notice and this permission notice shall be included in
 *    all copies or substantial portions of the Software.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *    THE SOFTWARE.
 */

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import m.nischal.melody.Util.ObservableContainer;
import m.nischal.melody.ObjectModels._BaseModel;
import m.nischal.melody.R;
import m.nischal.melody.RecyclerViewHelpers.RecyclerItemClickListener;
import m.nischal.melody.RecyclerViewHelpers.RecyclerViewAdapter;
import rx.subscriptions.CompositeSubscription;

public class BaseFragment extends Fragment {

    private int fragmentType;
    private ArrayList<_BaseModel> baseModelArrayList = new ArrayList<>();
    private RecyclerView rv;
    private CompositeSubscription subscriptions = new CompositeSubscription();

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentType = getArguments().getInt(_BaseModel.VIEW_PAGER_POSITION_STRING);
        return inflater.inflate(R.layout.recycler_view, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = getActivity().getApplicationContext();
        rv = (RecyclerView) view.findViewById(R.id.recycler_view);
        rv.setLayoutManager(new GridLayoutManager(context, 2));
        populateList();
        rv.addOnItemTouchListener(new RecyclerItemClickListener(context));
        rv.addOnScrollListener(((MainActivity) getActivity()).getRecyclerScrollListenerInstance());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (subscriptions.hasSubscriptions())
            subscriptions.unsubscribe();
    }

    private void setAdapter() {
        rv.setAdapter(new RecyclerViewAdapter(baseModelArrayList));
    }

    private void populateList() {
        switch (fragmentType) {
            case _BaseModel.ALBUMS:
                subscriptions.add(ObservableContainer
                        .getAlbumArrayListObservable()
                        .doOnCompleted(this::setAdapter)
                        .subscribe(baseModelArrayList::add));
                break;

            case _BaseModel.SONGS:
                subscriptions.add(ObservableContainer
                        .getSongArrayListObservable()
                        .doOnCompleted(this::setAdapter)
                        .subscribe(baseModelArrayList::add));
                break;

            case _BaseModel.ARTISTS:
                subscriptions.add(ObservableContainer
                        .getArtistArrayListObservable()
                        .doOnCompleted(this::setAdapter)
                        .subscribe(baseModelArrayList::add));
                break;

            case _BaseModel.PLAYLISTS:
                subscriptions.add(ObservableContainer
                        .getPlaylistArrayListObservable()
                        .doOnCompleted(this::setAdapter)
                        .subscribe(baseModelArrayList::add));
                break;

            case _BaseModel.GENERS:
                subscriptions.add(ObservableContainer
                        .getGenreArrayListObservable()
                        .doOnCompleted(this::setAdapter)
                        .subscribe(baseModelArrayList::add));
                break;
        }
    }
}
